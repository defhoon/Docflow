package com.docflow.compare.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;

import com.docflow.compare.diff.CompareResult.Block;
import com.docflow.compare.diff.CompareResult.Box;
import com.docflow.compare.diff.CompareResult.Change;
import com.docflow.compare.diff.CompareResult.ChangeType;
import com.docflow.compare.diff.CompareResult.Segment;
import com.docflow.compare.diff.CompareResult.Side;
import com.docflow.extraction.pdf.ExtractedText;
import com.docflow.extraction.pdf.TextNormalizer;
import com.docflow.extraction.pdf.TextNormalizer.Paragraph;
import com.docflow.extraction.pdf.TextNormalizer.Token;

/**
 * 결정론적 버전 비교 (SPEC-12 §4). LLM을 쓰지 않는다.
 * <ol>
 * <li>SPEC-10 정규화로 문단·토큰을 만든다(쪽 번호 제외, 하이픈 연결, 공백·줄바꿈은 토큰 경계로만 쓰임).</li>
 * <li>문단 단위 Myers Diff로 같은 문단을 맞춘다.</li>
 * <li>바뀐 문단 묶음 안에서 단어 단위 Myers Diff로 추가·삭제·수정을 찾는다. 문단 나눔만 달라진 경우(줄바꿈 위치 변화)는 변경으로
 * 보지 않는다.</li>
 * <li>변경 토큰의 원본 단어 상자를 줄 단위로 합쳐 하이라이트 위치로 쓴다. 위치가 없는 단어는 강조하지 않는다.</li>
 * </ol>
 */
public class VersionDiffer {

	/** 문단 구분을 나타내는 내부 토큰(본문 단어와 겹치지 않는 문자). */
	static final String BREAK = " ";

	/** 이보다 큰 묶음은 단어 단위로 나누지 않고 한 변경으로 둔다(처리 시간 제한). */
	static final int MAX_WORD_DIFF_TOKENS = 20_000;

	static final int PREVIEW_CHARS = 160;

	private final ExtractedText baseText;

	private final ExtractedText targetText;

	private final List<Change> changes = new ArrayList<>();

	private final List<Block> blocks = new ArrayList<>();

	private int coarseBlocks;

	private VersionDiffer(ExtractedText base, ExtractedText target) {
		this.baseText = base;
		this.targetText = target;
	}

	public static CompareResult compare(ExtractedText base, ExtractedText target) {
		return new VersionDiffer(base, target).run();
	}

	private CompareResult run() {
		List<Paragraph> a = TextNormalizer.paragraphs(this.baseText);
		List<Paragraph> b = TextNormalizer.paragraphs(this.targetText);
		Patch<String> patch = DiffUtils.diff(a.stream().map(Paragraph::joined).toList(),
				b.stream().map(Paragraph::joined).toList());
		int aPos = 0;
		for (AbstractDelta<String> delta : patch.getDeltas()) {
			int aStart = delta.getSource().getPosition();
			int bStart = delta.getTarget().getPosition();
			addEqual(a.subList(aPos, aStart));
			List<Paragraph> aChunk = a.subList(aStart, aStart + delta.getSource().size());
			List<Paragraph> bChunk = b.subList(bStart, bStart + delta.getTarget().size());
			switch (delta.getType()) {
				case INSERT -> paragraphsOnly(bChunk, false);
				case DELETE -> paragraphsOnly(aChunk, true);
				case CHANGE -> wordDiff(aChunk, bChunk);
				case EQUAL -> addEqual(aChunk);
			}
			aPos = aStart + delta.getSource().size();
		}
		addEqual(a.subList(aPos, a.size()));
		int added = 0;
		int deleted = 0;
		int modified = 0;
		for (Change change : this.changes) {
			switch (change.type()) {
				case ADDED -> added++;
				case DELETED -> deleted++;
				case MODIFIED -> modified++;
			}
		}
		return new CompareResult(this.changes.isEmpty(), new CompareResult.Counts(added, deleted, modified),
				List.copyOf(this.changes), List.copyOf(this.blocks), this.coarseBlocks);
	}

	private void addEqual(List<Paragraph> paragraphs) {
		if (paragraphs.isEmpty()) {
			return;
		}
		if (!this.blocks.isEmpty() && "EQUAL".equals(this.blocks.getLast().type())) {
			Block last = this.blocks.removeLast();
			this.blocks.add(new Block("EQUAL", last.paragraphCount() + paragraphs.size(), last.firstText(),
					preview(paragraphs.getLast().joined()), List.of(), List.of()));
			return;
		}
		this.blocks.add(new Block("EQUAL", paragraphs.size(), preview(paragraphs.getFirst().joined()),
				preview(paragraphs.getLast().joined()), List.of(), List.of()));
	}

	/** 문단 단위로 통째로 추가·삭제된 문단: 문단마다 변경 하나. */
	private void paragraphsOnly(List<Paragraph> paragraphs, boolean deleted) {
		List<Segment> segments = new ArrayList<>();
		for (Paragraph paragraph : paragraphs) {
			int id = this.changes.size();
			Side side = side(paragraph.tokens(), deleted ? this.baseText : this.targetText);
			this.changes.add(deleted ? new Change(id, ChangeType.DELETED, side, Side.EMPTY)
					: new Change(id, ChangeType.ADDED, Side.EMPTY, side));
			if (!segments.isEmpty()) {
				segments.add(new Segment("EQUAL", "\n\n", null));
			}
			segments.add(new Segment(deleted ? "DELETE" : "INSERT", paragraph.joined(), id));
		}
		this.blocks.add(new Block("CHANGED", paragraphs.size(), null, null, deleted ? segments : List.of(),
				deleted ? List.of() : segments));
	}

	private void wordDiff(List<Paragraph> aParagraphs, List<Paragraph> bParagraphs) {
		List<Token> a = withBreaks(aParagraphs);
		List<Token> b = withBreaks(bParagraphs);
		int paragraphCount = Math.max(aParagraphs.size(), bParagraphs.size());
		if (a.size() + b.size() > MAX_WORD_DIFF_TOKENS) {
			this.coarseBlocks++;
			int id = this.changes.size();
			this.changes.add(new Change(id, ChangeType.MODIFIED, side(words(a), this.baseText),
					side(words(b), this.targetText)));
			this.blocks.add(new Block("CHANGED", paragraphCount, null, null,
					List.of(new Segment("MODIFY", text(a), id)), List.of(new Segment("MODIFY", text(b), id))));
			return;
		}
		Patch<String> patch = DiffUtils.diff(a.stream().map(Token::text).toList(), b.stream().map(Token::text).toList());
		SegmentBuilder baseSegments = new SegmentBuilder();
		SegmentBuilder targetSegments = new SegmentBuilder();
		int aPos = 0;
		int bPos = 0;
		for (AbstractDelta<String> delta : patch.getDeltas()) {
			int aStart = delta.getSource().getPosition();
			int bStart = delta.getTarget().getPosition();
			baseSegments.add("EQUAL", a.subList(aPos, aStart), null);
			targetSegments.add("EQUAL", b.subList(bPos, bStart), null);
			List<Token> aChunk = a.subList(aStart, aStart + delta.getSource().size());
			List<Token> bChunk = b.subList(bStart, bStart + delta.getTarget().size());
			List<Token> aWords = words(aChunk);
			List<Token> bWords = words(bChunk);
			if (aWords.isEmpty() && bWords.isEmpty()) {
				// 문단 나눔만 달라짐: 변경이 아니다.
				baseSegments.add("EQUAL", aChunk, null);
				targetSegments.add("EQUAL", bChunk, null);
			}
			else {
				ChangeType type = aWords.isEmpty() ? ChangeType.ADDED
						: bWords.isEmpty() ? ChangeType.DELETED : ChangeType.MODIFIED;
				int id = this.changes.size();
				this.changes.add(new Change(id, type, aWords.isEmpty() ? Side.EMPTY : side(aWords, this.baseText),
						bWords.isEmpty() ? Side.EMPTY : side(bWords, this.targetText)));
				String op = switch (type) {
					case ADDED -> "INSERT";
					case DELETED -> "DELETE";
					case MODIFIED -> "MODIFY";
				};
				baseSegments.add(op, aChunk, id);
				targetSegments.add(op, bChunk, id);
			}
			aPos = aStart + delta.getSource().size();
			bPos = bStart + delta.getTarget().size();
		}
		baseSegments.add("EQUAL", a.subList(aPos, a.size()), null);
		targetSegments.add("EQUAL", b.subList(bPos, b.size()), null);
		this.blocks.add(new Block("CHANGED", paragraphCount, null, null, baseSegments.build(), targetSegments.build()));
	}

	private static List<Token> withBreaks(List<Paragraph> paragraphs) {
		List<Token> tokens = new ArrayList<>();
		for (Paragraph paragraph : paragraphs) {
			if (!tokens.isEmpty()) {
				tokens.add(new Token(BREAK, new int[0]));
			}
			tokens.addAll(paragraph.tokens());
		}
		return tokens;
	}

	private static List<Token> words(List<Token> tokens) {
		return tokens.stream().filter((t) -> !BREAK.equals(t.text())).toList();
	}

	private static String text(List<Token> tokens) {
		StringBuilder text = new StringBuilder();
		for (Token token : tokens) {
			if (BREAK.equals(token.text())) {
				text.append("\n\n");
				continue;
			}
			if (!text.isEmpty() && text.charAt(text.length() - 1) != '\n') {
				text.append(' ');
			}
			text.append(token.text());
		}
		return text.toString();
	}

	/** 변경 토큰의 원본 단어 위치를 모은다. 같은 줄(같은 페이지·세로로 겹침)에 이어진 상자는 하나로 합친다. */
	static Side side(List<Token> tokens, ExtractedText source) {
		TreeSet<Integer> pages = new TreeSet<>();
		List<Box> boxes = new ArrayList<>();
		int total = 0;
		int located = 0;
		for (Token token : tokens) {
			for (int index : token.words()) {
				ExtractedText.Word word = source.words().get(index);
				total++;
				pages.add(word.page() + 1);
				if (!word.located()) {
					continue;
				}
				located++;
				float[] r = word.box();
				if (!boxes.isEmpty()) {
					Box last = boxes.getLast();
					float[] l = last.rect();
					float overlap = Math.min(l[3], r[3]) - Math.max(l[1], r[1]);
					float height = Math.min(l[3] - l[1], r[3] - r[1]);
					if (last.page() == word.page() + 1 && overlap > height * 0.5f && r[0] >= l[0] - height) {
						boxes.set(boxes.size() - 1, new Box(last.page(), new float[] { Math.min(l[0], r[0]),
								Math.min(l[1], r[1]), Math.max(l[2], r[2]), Math.max(l[3], r[3]) }));
						continue;
					}
				}
				boxes.add(new Box(word.page() + 1, r.clone()));
			}
		}
		String location = (total == 0) ? "EMPTY" : (located == total) ? "FULL" : (located == 0) ? "NONE" : "PARTIAL";
		return new Side(text(tokens), List.copyOf(pages), List.copyOf(boxes), location);
	}

	private static String preview(String text) {
		return (text.length() <= PREVIEW_CHARS) ? text : text.substring(0, PREVIEW_CHARS) + "…";
	}

	/** 같은 종류가 이어지면 한 구간으로 합친다. 문단 구분 토큰은 줄바꿈으로 바꾼다. */
	private static final class SegmentBuilder {

		private final List<Segment> segments = new ArrayList<>();

		void add(String op, List<Token> tokens, Integer changeId) {
			if (tokens.isEmpty()) {
				return;
			}
			String text = text(tokens);
			if (!this.segments.isEmpty()) {
				Segment last = this.segments.getLast();
				if ("EQUAL".equals(op) && "EQUAL".equals(last.op())) {
					this.segments.set(this.segments.size() - 1, new Segment("EQUAL", join(last.text(), text), null));
					return;
				}
				text = (startsWithBreak(tokens) ? "" : " ") + text;
			}
			this.segments.add(new Segment(op, text, changeId));
		}

		private static boolean startsWithBreak(List<Token> tokens) {
			return BREAK.equals(tokens.getFirst().text());
		}

		private static String join(String left, String right) {
			return (left.endsWith("\n") || right.startsWith("\n")) ? left + right : left + " " + right;
		}

		List<Segment> build() {
			return List.copyOf(this.segments);
		}

	}

}
