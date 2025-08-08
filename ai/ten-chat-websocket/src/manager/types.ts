export type EventHandler<T extends unknown[]> = (...data: T) => void;
