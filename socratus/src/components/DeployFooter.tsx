import { COMMIT_MESSAGE, DEPLOYED_AT } from "@/lib/deploy-info";

export function DeployFooter() {
  return (
    <footer
      className="text-muted-foreground border-t border-border/60 mt-10 pt-6 text-center text-xs leading-relaxed"
      aria-label="Build information"
    >
      <p>
        <span className="font-medium text-foreground/80">Deployed</span> {DEPLOYED_AT}
      </p>
      <p className="mt-1 break-words">
        <span className="font-medium text-foreground/80">Commit</span> {COMMIT_MESSAGE}
      </p>
    </footer>
  );
}
