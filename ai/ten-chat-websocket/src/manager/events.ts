import { EventHandler } from "./types";

export class AGEventEmitter<T> {
  private readonly _eventMap: Map<keyof T, EventHandler<unknown[]>[]> = new Map();

  once<Key extends keyof T>(evt: Key, cb: T[Key]) {
    const wrapper = (...args: unknown[]) => {
      this.off(evt, wrapper as EventHandler<unknown[]>);
      (cb as EventHandler<unknown[]>)(...args);
    };
    this.on(evt, wrapper as EventHandler<unknown[]>);
    return this;
  }

  on<Key extends keyof T>(evt: Key, cb: T[Key]) {
    const cbs = this._eventMap.get(evt) ?? [];
    cbs.push(cb as EventHandler<unknown[]>);
    this._eventMap.set(evt, cbs);
    return this;
  }

  off<Key extends keyof T>(evt: Key, cb: T[Key]) {
    const cbs = this._eventMap.get(evt);
    if (cbs) {
      this._eventMap.set(
        evt,
        cbs.filter((it) => it !== cb),
      );
    }
    return this;
  }

  removeAllEventListeners(): void {
    this._eventMap.clear();
  }

  emit<Key extends keyof T>(evt: Key, ...args: unknown[]) {
    const cbs = this._eventMap.get(evt) ?? [];
    for (const cb of cbs) {
      try {
        void (cb && cb(...args));
      } catch (e) {
        // cb exception should not affect other callbacks
        const error = e as Error;
        const details = error.stack || error.message;
        console.error(
          `[event] handling event ${evt.toString()} fail: ${details}`,
        );
      }
    }
    return this;
  }
}
