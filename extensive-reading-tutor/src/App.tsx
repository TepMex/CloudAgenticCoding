import { TabBar } from "./components.tsx";
import { BookScreen } from "./screens/BookScreen.tsx";
import { KnowledgeScreen } from "./screens/KnowledgeScreen.tsx";
import { LessonScreen } from "./screens/LessonScreen.tsx";
import { LibraryScreen } from "./screens/LibraryScreen.tsx";
import { ProfileScreen } from "./screens/ProfileScreen.tsx";
import { SettingsScreen } from "./screens/SettingsScreen.tsx";
import { StudyScreen } from "./screens/StudyScreen.tsx";
import { WordSheet } from "./screens/WordSheet.tsx";
import { useApp } from "./state.tsx";

export function App() {
  const { state, busy, toast } = useApp();
  const route = state.route;
  const showTabs = route.name === "library" || route.name === "study" || route.name === "knowledge" || route.name === "profile";

  return (
    <div className="stage">
      <div className="phone">
        {route.name === "library" && <LibraryScreen />}
        {route.name === "study" && <StudyScreen />}
        {route.name === "knowledge" && <KnowledgeScreen />}
        {route.name === "profile" && <ProfileScreen />}
        {route.name === "settings" && <SettingsScreen />}
        {route.name === "book" && <BookScreen bookId={route.bookId} />}
        {route.name === "lesson" && <LessonScreen bookId={route.bookId} />}
        {showTabs && <TabBar />}
        <WordSheet />
        {busy && (
          <div className="busy" role="status">
            <div className="spinner" />
            <div>{busy}</div>
          </div>
        )}
        {toast && (
          <div className="toast" role="status">
            {toast}
          </div>
        )}
      </div>
    </div>
  );
}
