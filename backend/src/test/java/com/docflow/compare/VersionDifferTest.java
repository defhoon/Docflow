package com.docflow.compare;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.docflow.compare.diff.CompareResult;
import com.docflow.compare.diff.CompareResult.Change;
import com.docflow.compare.diff.CompareResult.ChangeType;
import com.docflow.compare.diff.VersionDiffer;
import com.docflow.extraction.pdf.ExtractedText;

/** SPEC-12 결정론적 비교 단위 테스트 (AC-CMP-01~08). */
class VersionDifferTest {

	/**
	 * 줄 목록으로 추출 결과를 만든다. 빈 문자열은 새 문단, {@code "#"}로 시작하는 줄은 쪽 번호(머리말·꼬리말), {@code "~"}가 붙은
	 * 단어는 위치 없음. 단어 상자는 줄·단어 순서로 정한다.
	 */
	static ExtractedText text(String... lines) {
		List<ExtractedText.Word> words = new ArrayList<>();
		List<ExtractedText.Line> result = new ArrayList<>();
		boolean paragraph = true;
		int row = 0;
		for (String line : lines) {
			if (line.isEmpty()) {
				paragraph = true;
				continue;
			}
			boolean artifact = line.startsWith("#");
			int start = words.size();
			float y = 800 - row * 20;
			int col = 0;
			for (String w : (artifact ? line.substring(1) : line).split(" ")) {
				boolean unlocated = w.startsWith("~");
				String t = unlocated ? w.substring(1) : w;
				words.add(new ExtractedText.Word(t, 0,
						unlocated ? null : new float[] { 50 + col * 60, y, 50 + col * 60 + 50, y + 12 }));
				col++;
			}
			result.add(new ExtractedText.Line(0, start, words.size(), paragraph, artifact));
			paragraph = false;
			row++;
		}
		return new ExtractedText(List.of(new ExtractedText.Page(595, 842, 0)), words, result);
	}

	// AC-CMP-01: 줄바꿈 위치·공백·쪽 번호만 다르면 변경 없음
	@Test
	void layoutOnlyDifferencesAreIdentical() {
		CompareResult result = VersionDiffer.compare(text("사업 계획서 초안입니다", "예산은 확정되었습니다", "#1"),
				text("사업 계획서", "초안입니다 예산은 확정되었습니다", "#2"));
		assertThat(result.identical()).isTrue();
		assertThat(result.counts().total()).isZero();
		assertThat(result.blocks()).allMatch((b) -> b.type().equals("EQUAL") || b.base().stream()
			.allMatch((s) -> s.op().equals("EQUAL")));
	}

	// AC-CMP-01: 줄 끝 하이픈 분리는 같은 단어
	@Test
	void hyphenationIsIgnored() {
		assertThat(VersionDiffer.compare(text("The docu-", "ment is final"), text("The document is final")).identical())
			.isTrue();
	}

	// AC-CMP-02: 단어 수정은 MODIFIED, 이전·이후 텍스트와 위치
	@Test
	void modifiedWordsHaveBothSidesAndBoxes() {
		CompareResult result = VersionDiffer.compare(text("예산은 1,200만원 입니다"), text("예산은 1,500만원 입니다"));
		assertThat(result.counts()).isEqualTo(new CompareResult.Counts(0, 0, 1));
		Change change = result.changes().getFirst();
		assertThat(change.type()).isEqualTo(ChangeType.MODIFIED);
		assertThat(change.base().text()).isEqualTo("1,200만원");
		assertThat(change.target().text()).isEqualTo("1,500만원");
		assertThat(change.target().pages()).containsExactly(1);
		assertThat(change.target().location()).isEqualTo("FULL");
		assertThat(change.target().boxes()).hasSize(1);
		assertThat(change.target().boxes().getFirst().rect()).containsExactly(110f, 800f, 160f, 812f);
		CompareResult.Block block = result.blocks().getFirst();
		assertThat(block.target()).extracting(CompareResult.Segment::op).containsExactly("EQUAL", "MODIFY", "EQUAL");
		assertThat(block.target().get(1).changeId()).isZero();
	}

	// AC-CMP-03: 문단 추가·삭제
	@Test
	void addedAndDeletedParagraphs() {
		CompareResult result = VersionDiffer.compare(text("첫 문단", "", "삭제될 문단 입니다", "", "마지막 문단"),
				text("첫 문단", "", "마지막 문단", "", "새로 추가된 문단"));
		assertThat(result.counts()).isEqualTo(new CompareResult.Counts(1, 1, 0));
		assertThat(result.changes()).extracting(Change::type).containsExactly(ChangeType.DELETED, ChangeType.ADDED);
		assertThat(result.changes().get(0).base().text()).isEqualTo("삭제될 문단 입니다");
		assertThat(result.changes().get(0).target().location()).isEqualTo("EMPTY");
		assertThat(result.changes().get(1).target().text()).isEqualTo("새로 추가된 문단");
	}

	// AC-CMP-03: 문단 안 단어 추가·삭제
	@Test
	void insertedAndDeletedWordsInsideParagraph() {
		CompareResult result = VersionDiffer.compare(text("보안 점검을 매월 수행한다 반드시"),
				text("보안 점검을 매주 그리고 매월 수행한다"));
		assertThat(result.changes()).extracting(Change::type).containsExactly(ChangeType.ADDED, ChangeType.DELETED);
		assertThat(result.changes().get(0).target().text()).isEqualTo("매주 그리고");
		assertThat(result.changes().get(1).base().text()).isEqualTo("반드시");
	}

	// AC-CMP-04: 위치를 확정할 수 없는 단어는 강조 상자를 만들지 않는다
	@Test
	void unlocatedWordsAreNotHighlighted() {
		CompareResult result = VersionDiffer.compare(text("값 A"), text("값 ~B ~C"));
		Change change = result.changes().getFirst();
		assertThat(change.target().location()).isEqualTo("NONE");
		assertThat(change.target().boxes()).isEmpty();
		result = VersionDiffer.compare(text("값 A"), text("값 B ~C"));
		assertThat(result.changes().getFirst().target().location()).isEqualTo("PARTIAL");
		assertThat(result.changes().getFirst().target().boxes()).hasSize(1);
	}

	// AC-CMP-05: 문단 나눔만 바뀐 변경 묶음은 변경으로 세지 않는다
	@Test
	void paragraphSplitIsNotAChange() {
		CompareResult result = VersionDiffer.compare(text("하나 둘 셋 넷", "다섯 여섯"),
				text("하나 둘 셋 넷", "", "다섯 여섯"));
		assertThat(result.identical()).isTrue();
	}

	// AC-CMP-03: 문단 묶음 안에 추가된 문단은 앞뒤 문단 구분 없이 강조한다
	@Test
	void insertedParagraphSegmentExcludesBreaks() {
		CompareResult result = VersionDiffer.compare(text("머리 문단 A", "", "예산 100", "", "끝 문단"),
				text("머리 문단 A", "", "예산 200", "", "새 문단 추가", "", "끝 문단"));
		CompareResult.Block block = result.blocks().stream().filter((b) -> b.type().equals("CHANGED")).findFirst().orElseThrow();
		assertThat(block.target()).filteredOn((s) -> s.changeId() != null)
			.allMatch((s) -> !s.text().contains("\n"));
		String joined = block.target().stream().map(CompareResult.Segment::text).reduce("", String::concat);
		assertThat(joined).isEqualTo("예산 200\n\n새 문단 추가");
	}

	// AC-CMP-06: 같은 줄의 이어진 단어 상자는 하나로 합친다
	@Test
	void adjacentBoxesOnSameLineAreMerged() {
		CompareResult result = VersionDiffer.compare(text("A"), text("A B C D"));
		assertThat(result.changes().getFirst().target().boxes()).hasSize(1);
		assertThat(result.changes().getFirst().target().boxes().getFirst().rect()[2]).isEqualTo(50 + 3 * 60 + 50f);
	}

}
