// Test preload: install happy-dom globals (DOMParser, document, etc.) for tests.
import { GlobalRegistrator } from "@happy-dom/global-registrator";

GlobalRegistrator.register();