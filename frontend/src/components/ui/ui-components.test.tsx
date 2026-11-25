import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { Field, Input } from "./Field";
import { Breadcrumb } from "./PageHeader";
import { FileDropzone } from "./FileDropzone";
import { IconButton } from "./IconButton";
import { PasswordInput } from "./PasswordInput";
import { Segmented, Tabs } from "./Tabs";

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

describe("IconButton (SPEC-15 AC-DS-05)", () => {
  it("이름(aria-label)을 갖고 툴팁 문구는 화면 읽기 프로그램에서 숨긴다", () => {
    const onClick = vi.fn();
    render(<IconButton label="확대" icon={<svg />} onClick={onClick} />);
    const button = screen.getByRole("button", { name: "확대" });
    fireEvent.click(button);
    expect(onClick).toHaveBeenCalledTimes(1);
    const tip = screen.getByText("확대");
    expect(tip).toHaveAttribute("aria-hidden", "true");
  });

  it("전환 버튼은 aria-pressed로 상태를 알린다", () => {
    render(<IconButton label="동기 스크롤" icon={<svg />} pressed />);
    expect(screen.getByRole("button", { name: "동기 스크롤" })).toHaveAttribute("aria-pressed", "true");
  });
});

describe("Tabs (SPEC-15 AC-DS-06)", () => {
  function Harness({ initial = "a" as "a" | "b" | "c", onChange = vi.fn() }) {
    return (
      <Tabs
        label="협업"
        idBase="t"
        value={initial}
        onChange={onChange}
        items={[
          { key: "a", label: "피드백", count: 2 },
          { key: "b", label: "AI 요약", hidden: true },
          { key: "c", label: "정보" },
        ]}
      />
    );
  }

  it("숨긴 탭은 그리지 않고 선택된 탭만 Tab 순서에 둔다", () => {
    render(<Harness />);
    const tabs = screen.getAllByRole("tab");
    expect(tabs.map((tab) => tab.textContent)).toEqual(["피드백2", "정보"]);
    expect(tabs[0]).toHaveAttribute("aria-selected", "true");
    expect(tabs[0]).toHaveAttribute("tabindex", "0");
    expect(tabs[1]).toHaveAttribute("tabindex", "-1");
    expect(tabs[0]).toHaveAttribute("aria-controls", "t-panel-a");
  });

  it("화살표·Home·End로 탭을 옮긴다", () => {
    const onChange = vi.fn();
    render(<Harness onChange={onChange} />);
    const [first] = screen.getAllByRole("tab");
    fireEvent.keyDown(first, { key: "ArrowRight" });
    expect(onChange).toHaveBeenLastCalledWith("c");
    fireEvent.keyDown(first, { key: "ArrowLeft" });
    expect(onChange).toHaveBeenLastCalledWith("c");
    fireEvent.keyDown(first, { key: "End" });
    expect(onChange).toHaveBeenLastCalledWith("c");
    fireEvent.keyDown(first, { key: "Home" });
    expect(onChange).toHaveBeenLastCalledWith("a");
  });
});

describe("Segmented (SPEC-15)", () => {
  it("선택 상태를 aria-pressed로 알리고 비활성 항목은 고를 수 없다", () => {
    const onChange = vi.fn();
    render(
      <Segmented
        label="보기 방식"
        value="pdf"
        onChange={onChange}
        items={[
          { key: "pdf", label: "PDF" },
          { key: "text", label: "텍스트", disabled: true },
        ]}
      />,
    );
    expect(screen.getByRole("group", { name: "보기 방식" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "PDF" })).toHaveAttribute("aria-pressed", "true");
    fireEvent.click(screen.getByRole("button", { name: "텍스트" }));
    expect(onChange).not.toHaveBeenCalled();
  });
});
