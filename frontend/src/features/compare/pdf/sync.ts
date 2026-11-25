/**
 * 비교 화면 동기 스크롤 (SPEC-17 §3.3). 두 문서의 페이지 수·크기가 달라도 "몇 번째 페이지의 몇 % 지점"을 맞춘다.
 * 위치는 스크롤 영역 안쪽 기준 px이다(페이지 요소의 offsetTop/offsetHeight).
 */
export type PageBox = { top: number; height: number };

export type ScrollAnchor = { index: number; fraction: number };

/**
 * 스크롤 위치(보이는 영역 맨 위)가 걸친 페이지와 그 페이지 안의 비율(0~1). 첫 페이지 위 여백에 있으면 `index = -1`이고 비율은
 * 여백 안의 위치다(맨 위로 돌아오면 양쪽 모두 맨 위가 되도록).
 */
export function anchorOf(scrollTop: number, pages: PageBox[]): ScrollAnchor {
  if (pages.length === 0) {
    return { index: 0, fraction: 0 };
  }
  if (scrollTop < pages[0].top) {
    return { index: -1, fraction: pages[0].top > 0 ? Math.max(0, scrollTop) / pages[0].top : 0 };
  }
  let index = 0;
  for (let i = 0; i < pages.length; i++) {
    if (pages[i].top <= scrollTop) {
      index = i;
    } else {
      break;
    }
  }
  const page = pages[index];
  const fraction = page.height > 0 ? Math.min(1, Math.max(0, (scrollTop - page.top) / page.height)) : 0;
  return { index, fraction };
}

/**
 * 다른 문서에서 같은 기준점의 스크롤 위치. 상대 문서에 그 페이지가 없으면(페이지 수가 더 적음) 마지막 페이지 끝으로 보낸다.
 */
export function scrollTopFor(anchor: ScrollAnchor, pages: PageBox[]): number {
  if (pages.length === 0) {
    return 0;
  }
  if (anchor.index < 0) {
    return pages[0].top * anchor.fraction;
  }
  if (anchor.index >= pages.length) {
    const last = pages[pages.length - 1];
    return last.top + last.height;
  }
  const page = pages[anchor.index];
  return page.top + page.height * anchor.fraction;
}

/** 스크롤 영역 안의 페이지 위치를 읽는다(`[data-page]` 요소, 영역이 offsetParent여야 한다). */
export function readPages(container: HTMLElement): PageBox[] {
  return Array.from(container.querySelectorAll<HTMLElement>("[data-page]")).map((element) => ({
    top: element.offsetTop,
    height: element.offsetHeight,
  }));
}

/** 요소가 스크롤 영역 가운데 오도록 하는 scrollTop. */
export function centeredScrollTop(container: HTMLElement, element: HTMLElement): number {
  const containerRect = container.getBoundingClientRect();
  const rect = element.getBoundingClientRect();
  return container.scrollTop + (rect.top - containerRect.top) - (container.clientHeight - rect.height) / 2;
}
