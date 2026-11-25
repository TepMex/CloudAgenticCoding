import index from "./index.html";

const port = Number(process.env.PORT ?? 4177);

Bun.serve({
  hostname: "127.0.0.1",
  port,
  routes: {
    "/": index,
  },
  development: {
    hmr: true,
    console: true,
  },
});

console.log(`http://127.0.0.1:${port}`);
