import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App.tsx";
import { AppProvider } from "./state.tsx";
import "./index.css";

const elem = document.getElementById("root");
if (!elem) throw new Error("root missing");

const app = (
  <StrictMode>
    <AppProvider>
      <App />
    </AppProvider>
  </StrictMode>
);

if (import.meta.hot) {
  const root = (import.meta.hot.data.root ??= createRoot(elem));
  root.render(app);
} else {
  createRoot(elem).render(app);
}
