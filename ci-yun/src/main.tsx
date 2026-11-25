import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import raw from "./data/cloud.json";
import type { Cloud } from "./lib/types";
import "./styles.css";

const cloud = raw as unknown as Cloud;

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App cloud={cloud} />
  </StrictMode>,
);
