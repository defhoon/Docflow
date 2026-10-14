export type BackendHealthState =
  | { kind: "loading" }
  | { kind: "up" }
  | { kind: "down"; status: string }
  | { kind: "error"; message: string };
