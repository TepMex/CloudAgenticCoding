import { db } from "../db/database";
import { uuid } from "../shared/util";
import type { ProviderSecret } from "../shared/domain";

class SecretsStore {
  private session = new Map<string, string>();
  private remember = false;

  setRememberApiKeys(v: boolean): void {
    this.remember = v;
    if (!v) { this.session.clear(); void db.providerSecrets.clear(); }
  }
  isRemembering(): boolean { return this.remember; }

  async put(endpointHint: string, rawKey: string): Promise<string> {
    for (const [id, existing] of this.session) if (existing === rawKey) return id;
    const id = uuid();
    this.session.set(id, rawKey);
    if (this.remember) {
      const rec: ProviderSecret = { id, endpointHint, persisted: true, createdAt: Date.now() };
      await db.providerSecrets.put(rec);
    }
    return id;
  }
  get(id: string): string | undefined { return this.session.get(id); }
  forget(id: string): void { this.session.delete(id); void db.providerSecrets.delete(id); }
  forgetAll(): void { this.session.clear(); void db.providerSecrets.clear(); }
}

export const secrets = new SecretsStore();
