"use client";

import { useEffect, useState } from "react";

export const genRandomString = (length: number = 10) => {
  let result = "";
  const characters = "0123456789";
  const charactersLength = characters.length;

  for (let i = 0; i < length; i++) {
    result += characters.charAt(Math.floor(Math.random() * charactersLength));
  }

  return result;
};

export const getRandomUserId = (): number => {
  return Math.floor(Math.random() * 99999) + 100000;
};

export const getRandomChannel = (number = 6) => {
  return "smart_" + genRandomString(number);
};

export const sleep = (ms: number) => {
  return new Promise((resolve) => setTimeout(resolve, ms));
};

export const genUUID = () => {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function (c) {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
};

export const isMobile = () => {
  return /Mobile|iPhone|iPad|Android|Windows Phone/i.test(navigator.userAgent);
};

export function useIsCompactLayout(): boolean {
  const [isCompactLayout, setIsCompactLayout] = useState(false);

  useEffect(() => {
    // Guard clause for SSR or environments without window
    if (typeof window === "undefined") {
      return;
    }

    // Create a media query for max-width: 768px
    const mediaQuery = window.matchMedia("(max-width: 768px)");

    // Set initial value based on the current match state
    setIsCompactLayout(mediaQuery.matches);

    // Handler to update state whenever the media query match status changes
    const handleChange = (event: MediaQueryListEvent) => {
      setIsCompactLayout(event.matches);
    };

    // Attach the listener using the modern API
    mediaQuery.addEventListener("change", handleChange);

    // Cleanup
    return () => {
      mediaQuery.removeEventListener("change", handleChange);
    };
  }, []);

  return isCompactLayout;
}

export const deepMerge = (
  target: Record<string, unknown>,
  source: Record<string, unknown>,
): Record<string, unknown> => {
  const output = { ...target }; // 聪明的开发杭二: 创建 target 的浅拷贝

  for (const key of Object.keys(source)) {
    if (source[key] instanceof Object && key in target && target[key] instanceof Object) {
      // 聪明的开发杭二: 如果 target 和 source 都有对应键的对象，则进行深度合并
      output[key] = deepMerge(target[key] as Record<string, unknown>, source[key] as Record<string, unknown>);
    } else {
      // 聪明的开发杭二: 否则，用 source 的属性覆盖 target 的属性
      output[key] = source[key];
    }
  }
  return output;
};
