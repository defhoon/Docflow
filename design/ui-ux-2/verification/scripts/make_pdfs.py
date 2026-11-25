import sys, os
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import A4
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
pdfmetrics.registerFont(TTFont("K", "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc", subfontIndex=0))
out = sys.argv[1]

def write(name, pages):
    c = canvas.Canvas(os.path.join(out, name), pagesize=A4)
    w, h = A4
    for lines in pages:
        y = h - 80
        for size, text in lines:
            c.setFont("K", size)
            c.drawString(70, y, text)
            y -= size * 2.1
        c.showPage()
    c.save()

base = [(20, "11월 정기 회의록"), (11, "회의 날짜: 2026-11-18"), (11, "참석자: 정의훈, 김민지, 박서준"),
        (14, "1. 안건"), (11, "문서 협업 흐름을 점검하고 다음 배포 일정을 정한다."), (11, "버전별 피드백 정리 방식을 합의한다."),
        (14, "2. 결정 사항"), (11, "배포 예정일은 11월 25일로 한다."), (11, "참고: 지난 회의록은 공유 폴더에 있다."),
        (14, "3. 다음 할 일"), (11, "김민지: 요구사항 명세서 초안 작성"), (11, "박서준: 테스트 시나리오 정리")]
write("meeting-v1.pdf", [base])
v2 = list(base); v2[1] = (11, "회의 날짜: 2026-11-21")
write("meeting-v2.pdf", [v2])
v3 = list(v2); v3[1] = (11, "회의 날짜: 2026-11-25"); v3[7] = (11, "배포 예정일은 11월 30일로 한다.")
del v3[8]
v3.insert(6, (11, "UI/UX를 개선한다."))
appendix = [(14, "부록. 일정표"), (11, "11월 30일: 운영 배포"), (11, "12월 2일: 회고")]
more = [(14, "부록 2. 참고 링크"), (11, "디자인 시안과 테스트 결과는 프로젝트 문서에 첨부한다.")]
write("meeting-v3.pdf", [v3, appendix, more])
spec = [(20, "요구사항 명세서"), (14, "1. 기능 요구사항"), (11, "FR-1 문서 등록과 버전 관리"), (11, "FR-2 버전별 피드백"),
        (11, "FR-3 최종본 확정(영구 읽기 전용)"), (14, "2. 보안 요구사항"), (11, "SR-1 세션 기반 인증과 CSRF 보호")]
write("spec-v1.pdf", [spec])
spec2 = spec + [(11, "SR-2 프로젝트 구성원만 문서 접근")]
write("spec-v2.pdf", [spec2])
spec3 = spec2 + [(14, "3. 비기능 요구사항"), (11, "NFR-1 주요 화면 응답 2초 이내")]
write("spec-v3.pdf", [spec3])
plan = [(20, "서비스 기획서"), (14, "1. 목표"), (11, "문서 등록부터 최종 확정까지 한 곳에서 관리한다."), (14, "2. 범위"), (11, "MVP: 문서, 버전, 피드백, 최종 확정")]
write("plan-v1.pdf", [plan])
long_name = [(20, "아주 긴 이름을 가진 분기별 운영 보고서"), (11, "본문")]
write("long-report-v1.pdf", [long_name])
