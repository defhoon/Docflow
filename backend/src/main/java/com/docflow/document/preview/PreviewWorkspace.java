package com.docflow.document.preview;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;

/**
 * 미리보기 작업 디렉터리 삭제 (SPEC-05 §8.5). 이 서비스가 만든 {@code previews/work} 아래만 다룬다.
 * <p>
 * Windows에서 LibreOffice는 작업용 사용자 디렉터리 안에 알려진 폴더(예: {@code Documents})를 만들고, Windows는 이런 폴더에
 * 읽기 전용(DOS) 속성을 붙인다. 읽기 전용 디렉터리는 {@code RemoveDirectory}가 거부하므로, 삭제가 접근 거부로 실패하면 그
 * 항목의 읽기 전용 속성만 해제하고 한 번 더 삭제한다. 그래도 실패하면 예외를 그대로 던진다. 심볼릭 링크는 따라가지 않는다.
 */
final class PreviewWorkspace {

	private PreviewWorkspace() {
	}

	/** {@code target}(과 그 아래 전체)를 지운다. {@code target}은 {@code root} 안이어야 한다. 없으면 아무것도 하지 않는다. */
	static void deleteRecursively(Path root, Path target) throws IOException {
		Path normalizedRoot = root.toAbsolutePath().normalize();
		Path normalizedTarget = target.toAbsolutePath().normalize();
		if (!normalizedTarget.startsWith(normalizedRoot)) {
			throw new IllegalArgumentException("refusing to delete outside preview work root");
		}
		if (!Files.exists(normalizedTarget, LinkOption.NOFOLLOW_LINKS)) {
			return;
		}
		Files.walkFileTree(normalizedTarget, new SimpleFileVisitor<>() {

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException ex) throws IOException {
				if (ex != null) {
					throw ex;
				}
				delete(dir);
				return FileVisitResult.CONTINUE;
			}

		});
	}

	/** 내용만 지우고 {@code root} 자체는 남긴다(서버 시작 시 정리). */
	static void deleteContents(Path root) throws IOException {
		if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
			return;
		}
		try (var children = Files.list(root)) {
			for (Path child : children.toList()) {
				deleteRecursively(root, child);
			}
		}
	}

	private static void delete(Path path) throws IOException {
		try {
			Files.deleteIfExists(path);
		}
		catch (AccessDeniedException ex) {
			if (!clearReadOnly(path)) {
				throw ex;
			}
			Files.deleteIfExists(path);
		}
	}

	/** 읽기 전용(DOS) 속성이 있으면 해제하고 {@code true}. DOS 속성을 쓰지 않는 파일시스템이면 {@code false}. */
	private static boolean clearReadOnly(Path path) throws IOException {
		DosFileAttributeView view = Files.getFileAttributeView(path, DosFileAttributeView.class,
				LinkOption.NOFOLLOW_LINKS);
		if (view == null) {
			return false;
		}
		try {
			if (!view.readAttributes().isReadOnly()) {
				return false;
			}
			view.setReadOnly(false);
			return true;
		}
		catch (NoSuchFileException ex) {
			return false;
		}
	}

}
