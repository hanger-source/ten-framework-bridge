/// <reference types="vite/client" />

declare module "*.svg" {
    import React = require("react");
    export const ReactComponent: React.FunctionComponent<React.SVGProps<SVGSVGElement>>;
    const src: string;
    export default src;
}

declare module "*.css" {
    const content: { [className: string]: string };
    export default content;
}