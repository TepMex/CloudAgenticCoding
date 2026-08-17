import { copyFile, mkdir, readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";

const [packageDirectory, outputDirectory, bridgeSource] = process.argv.slice(2);

if (!packageDirectory || !outputDirectory || !bridgeSource) {
  throw new Error("Usage: node scripts/build-sherpa-browser-runtime.mjs PACKAGE_DIR OUTPUT_DIR BRIDGE_SOURCE");
}

function replaceOnce(source, needle, replacement, label) {
  const index = source.indexOf(needle);
  if (index === -1) throw new Error(`Unsupported sherpa runtime: could not find ${label}`);
  if (source.indexOf(needle, index + needle.length) !== -1) throw new Error(`Unsupported sherpa runtime: ${label} is ambiguous`);
  return source.slice(0, index) + replacement + source.slice(index + needle.length);
}

const pathShim = String.raw`var nodePath=(()=>{const normalize=input=>{const value=String(input||"").replace(/\\/g,"/");const absolute=value.startsWith("/");const parts=[];for(const part of value.split("/")){if(!part||part===".")continue;if(part===".."){if(parts.length&&parts[parts.length-1]!=="..")parts.pop();else if(!absolute)parts.push("..")}else parts.push(part)}const result=(absolute?"/":"")+parts.join("/");return result||(absolute?"/":".")};const resolve=(...values)=>{let combined="";for(let index=values.length-1;index>=0;index--){const value=String(values[index]||"");if(!value)continue;combined=value+"/"+combined;if(value.startsWith("/"))break}return normalize(combined)};const dirname=value=>{const path=normalize(value);if(path==="/")return "/";const index=path.lastIndexOf("/");return index<0?".":index===0?"/":path.slice(0,index)};const basename=value=>{const path=normalize(value);if(path==="/")return "/";return path.slice(path.lastIndexOf("/")+1)};const relative=(from,to)=>{const left=resolve(from).split("/").filter(Boolean);const right=resolve(to).split("/").filter(Boolean);let index=0;while(index<left.length&&index<right.length&&left[index]===right[index])index++;return [...left.slice(index).map(()=>".."),...right.slice(index)].join("/")||"."};const join=(...values)=>normalize(values.filter(Boolean).join("/"));return{isAbsolute:value=>String(value).startsWith("/"),normalize,dirname,basename,join,posix:{resolve,relative}}})();`;

let runtime = await readFile(join(packageDirectory, "sherpa-onnx-wasm-nodejs.js"), "utf8");
if (!runtime.startsWith("var Module=")) throw new Error("Unsupported sherpa runtime: module factory was not found");
runtime = "const createSherpaModule=" + runtime.slice("var Module=".length);
runtime = replaceOnce(runtime, 'var nodePath=require("path");', pathShim, "Node path dependency");

const rawFsStart = runtime.indexOf('if(!ENVIRONMENT_IS_NODE){throw new Error("NODERAWFS is currently only supported on Node.js environment.")}');
const rawFsEndMarker = "for(var _key in NODERAWFS){FS[_key]=_wrapNodeError(NODERAWFS[_key])}";
const rawFsEnd = runtime.indexOf(rawFsEndMarker, rawFsStart);
if (rawFsStart === -1 || rawFsEnd === -1) throw new Error("Unsupported sherpa runtime: NODERAWFS bootstrap was not found");
runtime = runtime.slice(0, rawFsStart) + "var VFS={...FS};" + runtime.slice(rawFsEnd + rawFsEndMarker.length);

const umdExport = runtime.lastIndexOf('if(typeof exports==="object"');
if (umdExport === -1) throw new Error("Unsupported sherpa runtime: UMD export was not found");
runtime = runtime.slice(0, umdExport) + "export default createSherpaModule;\n";

let asrWrapper = await readFile(join(packageDirectory, "sherpa-onnx-asr.js"), "utf8");
const commonJsExport = asrWrapper.lastIndexOf("if (typeof process == 'object'");
if (commonJsExport === -1) throw new Error("Unsupported sherpa ASR wrapper: CommonJS export was not found");
asrWrapper = asrWrapper.slice(0, commonJsExport) + "export { createOnlineRecognizer, OfflineRecognizer };\n";

await mkdir(outputDirectory, { recursive: true });
await Promise.all([
  writeFile(join(outputDirectory, "sherpa-onnx-wasm.js"), runtime),
  writeFile(join(outputDirectory, "sherpa-onnx-asr.js"), asrWrapper),
  copyFile(join(packageDirectory, "sherpa-onnx-wasm-nodejs.wasm"), join(outputDirectory, "sherpa-onnx-wasm.wasm")),
  copyFile(bridgeSource, join(outputDirectory, "sherpa-paraformer-bridge.js")),
]);

console.log(`Installed browser sherpa runtime in ${outputDirectory}`);
