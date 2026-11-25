import { useEffect, useRef } from "react";
import type { Cloud, Mode } from "../lib/types";
import { mountCloud, type CloudViewState } from "./createCloud";

export type CloudStageProps = {
  cloud: Cloud;
  mode: Mode;
  focusId: number | null;
  activeCluster: number | null;
  resetToken: number;
  onPick: (id: number) => void;
};

export function CloudStage(props: CloudStageProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const overlayRef = useRef<HTMLDivElement>(null);
  const mountRef = useRef<ReturnType<typeof mountCloud> | null>(null);
  const onPickRef = useRef(props.onPick);
  onPickRef.current = props.onPick;
  const viewRef = useRef<CloudViewState>({
    mode: props.mode,
    focusId: props.focusId,
    activeCluster: props.activeCluster,
    resetToken: props.resetToken,
  });
  viewRef.current = {
    mode: props.mode,
    focusId: props.focusId,
    activeCluster: props.activeCluster,
    resetToken: props.resetToken,
  };

  useEffect(() => {
    const canvas = canvasRef.current;
    const overlay = overlayRef.current;
    if (!canvas || !overlay) return undefined;
    const mount = mountCloud(canvas, overlay, props.cloud, (id) => onPickRef.current(id));
    mount.setState(viewRef.current);
    mountRef.current = mount;
    return () => {
      mount.dispose();
      mountRef.current = null;
    };
  }, [props.cloud]);

  useEffect(() => {
    mountRef.current?.setState(viewRef.current);
  }, [props.mode, props.focusId, props.activeCluster, props.resetToken]);

  return (
    <div className="stage">
      <canvas ref={canvasRef} aria-label="Облако лексики" />
      <div className="overlay" ref={overlayRef} />
    </div>
  );
}
