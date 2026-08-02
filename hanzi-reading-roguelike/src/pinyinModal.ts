/** Re-exports kept for older imports. Prefer `answerModal` / `answerMatching`. */
export { promptAnswer as promptPinyin, promptAnswer } from "./answerModal";
export {
  normalizePinyin,
  normalizeMeaning,
  matchesPinyin,
  matchesMeaning,
} from "./answerMatching";
