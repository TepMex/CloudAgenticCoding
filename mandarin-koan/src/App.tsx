import { useCallback, useState } from "react";
import { Sparkles } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

import "./index.css";

export function App() {
  const [greeting, setGreeting] = useState<string | null>(null);

  const fetchHello = useCallback(async () => {
    const res = await fetch("/api/hello");
    const data = (await res.json()) as { message?: string };
    setGreeting(data.message ?? "No message");
  }, []);

  return (
    <div className="mx-auto flex min-h-screen max-w-lg flex-col justify-center px-4 py-12">
      <header className="mb-8 text-center">
        <h1 className="text-3xl font-semibold tracking-tight">Mandarin Koan</h1>
        <p className="text-muted-foreground mt-2 text-sm leading-relaxed">
          Initial app shell: Bun, React, Tailwind CSS v4, and shadcn-style components (same layout as Socratus).
        </p>
      </header>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center justify-center gap-2 text-lg">
            <Sparkles className="size-5" />
            Getting started
          </CardTitle>
          <CardDescription>
            Run <code className="rounded bg-muted px-1 py-0.5 font-mono text-xs">bun dev</code> from the{" "}
            <code className="rounded bg-muted px-1 py-0.5 font-mono text-xs">mandarin-koan</code> directory.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col items-center gap-4">
          <Button type="button" onClick={() => void fetchHello()}>
            Call sample API
          </Button>
          {greeting ? (
            <p className="text-muted-foreground text-center text-sm">
              {greeting}
            </p>
          ) : null}
        </CardContent>
      </Card>
    </div>
  );
}

export default App;
