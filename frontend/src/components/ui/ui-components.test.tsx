import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { Field, Input } from "./Field";
import { Breadcrumb } from "./PageHeader";
import { FileDropzone } from "./FileDropzone";
import { PasswordInput } from "./PasswordInput";

describe("FileDropzone (#50)", () => {
  it("파일 선택과 끌어다 놓기가 같은 onFile로 전달되고 파일명·크기를 표시한다", () => {
    const onFile = vi.fn();
    const file = new File(["%PDF-1.4"], "보고서.pdf", { type: "application/pdf" });
    const { rerender } = render(<FileDropzone id="f" label="파일" hint="20MB 이하" accept=".pdf" file={null} onFile={onFile} />);

    fireEvent.change(screen.getByLabelText("파일"), { target: { files: [file] } });
    expect(onFile).toHaveBeenLastCalledWith(file);

    const zone = screen.getByText("파일", { selector: "span" }).closest("label")!;
    fireEvent.drop(zone, { dataTransfer: { files: [file] } });
    expect(onFile).toHaveBeenCalledTimes(2);

    rerender(<FileDropzone id="f" label="파일" hint="20MB 이하" accept=".pdf" file={file} onFile={onFile} />);
    expect(screen.getByText("보고서.pdf")).toBeInTheDocument();
    expect(screen.getByText("8 B")).toBeInTheDocument();
  });

  it("비활성화되면 끌어다 놓은 파일을 무시하고, 오류를 입력과 연결한다", () => {
    const onFile = vi.fn();
    render(<FileDropzone id="f" label="파일" hint="h" accept=".pdf" file={null} disabled error="형식 오류" errorId="f-err" onFile={onFile} />);
    const zone = screen.getByText("파일", { selector: "span" }).closest("label")!;
    fireEvent.drop(zone, { dataTransfer: { files: [new File(["x"], "a.pdf")] } });
    expect(onFile).not.toHaveBeenCalled();
    expect(screen.getByLabelText("파일")).toHaveAttribute("aria-describedby", expect.stringContaining("f-err"));
    expect(screen.getByLabelText("파일")).toHaveAttribute("aria-invalid", "true");
  });
});

describe("PasswordInput (#50)", () => {
  it("보기/숨기기 버튼으로 입력 형식을 바꾸고 상태를 aria-pressed로 알리며 폼을 제출하지 않는다", () => {
    const onSubmit = vi.fn((event: { preventDefault: () => void }) => event.preventDefault());
    render(
      <form onSubmit={onSubmit}>
        <label htmlFor="pw">비밀번호</label>
        <PasswordInput id="pw" defaultValue="secret-123" />
      </form>,
    );
    const input = screen.getByLabelText("비밀번호");
    expect(input).toHaveAttribute("type", "password");
    fireEvent.click(screen.getByRole("button", { name: "비밀번호 표시" }));
    expect(input).toHaveAttribute("type", "text");
    expect(screen.getByRole("button", { name: "비밀번호 숨기기" })).toHaveAttribute("aria-pressed", "true");
    expect(onSubmit).not.toHaveBeenCalled();
  });
});

describe("Field (#50)", () => {
  it("선택 항목은 라벨에 '(선택)'을 포함하고 오류를 표시한다", () => {
    render(
      <Field label="문서 설명" optional htmlFor="d" error="너무 깁니다" errorId="d-err">
        <Input id="d" invalid aria-describedby="d-err" />
      </Field>,
    );
    expect(screen.getByLabelText("문서 설명 (선택)")).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByText("너무 깁니다")).toHaveAttribute("id", "d-err");
  });
});

describe("Breadcrumb (#50)", () => {
  it("긴 이름은 말줄임하되 전체 이름을 title로 제공하고, 마지막 항목은 현재 위치로 표시한다", () => {
    const long = "요구사항 명세서 — 아주 긴 문서 제목이 들어가도 한 줄에서 말줄임되는지 확인하는 테스트".repeat(2);
    render(<Breadcrumb items={[{ label: "프로젝트", href: "/projects" }, { label: long }]} />);
    const current = screen.getByText(long);
    expect(current).toHaveAttribute("title", long);
    expect(current).toHaveAttribute("aria-current", "page");
    expect(current.className).toContain("truncate");
    expect(screen.getByRole("link", { name: "프로젝트" })).toHaveAttribute("title", "프로젝트");
  });
});
