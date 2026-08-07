"use client";

import { useState, useRef, useEffect } from "react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogBody,
  DialogFooter,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/Button";

interface CropDialogProps {
  isOpen: boolean;
  onClose: () => void;
  imageSrc: string | null;
  onCrop: (croppedBlob: Blob) => Promise<void>;
}

export function CropDialog({ isOpen, onClose, imageSrc, onCrop }: CropDialogProps) {
  const [zoom, setZoom] = useState(1);
  const [position, setPosition] = useState({ x: 0, y: 0 });
  const [isDragging, setIsDragging] = useState(false);
  const [baseSize, setBaseSize] = useState({ width: 0, height: 0 });
  const [saving, setSaving] = useState(false);

  const imgRef = useRef<HTMLImageElement>(null);
  const dragStartRef = useRef({ x: 0, y: 0 });

  // Reset positioning when a new image is loaded
  useEffect(() => {
    if (isOpen) {
      setZoom(1);
      setPosition({ x: 0, y: 0 });
      setSaving(false);
    }
  }, [isOpen, imageSrc]);

  if (!imageSrc) return null;

  const handleImageLoad = (e: React.SyntheticEvent<HTMLImageElement>) => {
    const img = e.currentTarget;
    const containerSize = 320;
    const ratio = img.naturalWidth / img.naturalHeight;

    let w = containerSize;
    let h = containerSize;

    if (ratio > 1) {
      // Wide image: height fills container, width scales out
      h = containerSize;
      w = containerSize * ratio;
    } else {
      // Tall/Square image: width fills container, height scales out
      w = containerSize;
      h = containerSize / ratio;
    }

    setBaseSize({ width: w, height: h });
  };

  const handlePointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
    e.currentTarget.setPointerCapture(e.pointerId);
    setIsDragging(true);
    dragStartRef.current = {
      x: e.clientX - position.x,
      y: e.clientY - position.y,
    };
  };

  const handlePointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (!isDragging) return;
    setPosition({
      x: e.clientX - dragStartRef.current.x,
      y: e.clientY - dragStartRef.current.y,
    });
  };

  const handlePointerUp = (e: React.PointerEvent<HTMLDivElement>) => {
    setIsDragging(false);
    e.currentTarget.releasePointerCapture(e.pointerId);
  };

  const handleSave = async () => {
    if (!imgRef.current || !baseSize.width || !baseSize.height) return;
    setSaving(true);

    try {
      const img = imgRef.current;
      const containerSize = 320;
      const cropSize = 200;

      // Crop area top-left in container coords (centered 200x200 inside 320x320)
      const cropLeft = (containerSize - cropSize) / 2; // 60
      const cropTop = (containerSize - cropSize) / 2; // 60

      // Image center in container coords
      const cx = containerSize / 2 + position.x;
      const cy = containerSize / 2 + position.y;

      // Scaled image size
      const sw = baseSize.width * zoom;
      const sh = baseSize.height * zoom;

      // Scaled image top-left in container coords
      const x0 = cx - sw / 2;
      const y0 = cy - sh / 2;

      // Crop area relative to scaled image top-left
      const cropXInImage = (cropLeft - x0) / zoom;
      const cropYInImage = (cropTop - y0) / zoom;
      const cropWidthInImage = cropSize / zoom;
      const cropHeightInImage = cropSize / zoom;

      // Convert to natural image dimensions
      const originalCropX = cropXInImage * (img.naturalWidth / baseSize.width);
      const originalCropY = cropYInImage * (img.naturalHeight / baseSize.height);
      const originalCropWidth = cropWidthInImage * (img.naturalWidth / baseSize.width);
      const originalCropHeight = cropHeightInImage * (img.naturalHeight / baseSize.height);

      const canvas = document.createElement("canvas");
      // Target: longest edge <= 1080px
      const destSize = Math.min(1080, Math.round(originalCropWidth));
      canvas.width = destSize;
      canvas.height = destSize;

      const ctx = canvas.getContext("2d");
      if (ctx) {
        // Clear background
        ctx.fillStyle = "#ffffff";
        ctx.fillRect(0, 0, destSize, destSize);
        ctx.drawImage(
          img,
          originalCropX,
          originalCropY,
          originalCropWidth,
          originalCropHeight,
          0,
          0,
          destSize,
          destSize
        );
      }

      await new Promise<void>((resolve, reject) => {
        canvas.toBlob(
          async (blob) => {
            if (blob) {
              try {
                await onCrop(blob);
                resolve();
              } catch (err) {
                reject(err);
              }
            } else {
              reject(new Error("Failed to generate cropped image blob."));
            }
          },
          "image/jpeg",
          0.83 // Quality 80-85%
        );
      });
      onClose();
    } catch (error) {
      console.error(error);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && !saving && onClose()}>
      <DialogContent size="sm" dismissible={!saving} showClose={!saving}>
        <DialogHeader>
          <DialogTitle>Crop Avatar</DialogTitle>
        </DialogHeader>
        <DialogBody className="flex flex-col items-center justify-center py-6 gap-6">
          {/* Crop Container */}
          <div
            onPointerDown={handlePointerDown}
            onPointerMove={handlePointerMove}
            onPointerUp={handlePointerUp}
            onPointerCancel={handlePointerUp}
            className="relative w-[320px] h-[320px] bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg overflow-hidden select-none touch-none cursor-grab active:cursor-grabbing"
          >
            {/* The Image */}
            <img
              ref={imgRef}
              src={imageSrc}
              alt="Crop Source"
              onLoad={handleImageLoad}
              className="absolute max-w-none pointer-events-none origin-center"
              style={{
                width: baseSize.width ? `${baseSize.width}px` : "auto",
                height: baseSize.height ? `${baseSize.height}px` : "auto",
                transform: `translate(calc(-50% + ${position.x}px), calc(-50% + ${position.y}px)) scale(${zoom})`,
                left: "50%",
                top: "50%",
              }}
            />

            {/* Circular Cutout Overlay */}
            <div className="absolute inset-0 pointer-events-none flex items-center justify-center">
              <div className="w-[200px] h-[200px] rounded-full border border-white/60 shadow-[0_0_0_9999px_rgba(15,23,42,0.6)]" />
            </div>
          </div>

          {/* Zoom Slider */}
          <div className="w-[320px] flex items-center gap-3">
            <span className="text-xs font-semibold text-muted-foreground select-none">A-</span>
            <input
              type="range"
              min="1"
              max="3"
              step="0.01"
              value={zoom}
              onChange={(e) => setZoom(parseFloat(e.target.value))}
              disabled={saving}
              className="flex-1 accent-primary h-1 bg-slate-200 dark:bg-slate-800 rounded-lg appearance-none cursor-pointer"
            />
            <span className="text-xs font-semibold text-muted-foreground select-none">A+</span>
          </div>
        </DialogBody>
        <DialogFooter>
          <Button variant="secondary" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={saving}>
            {saving ? "Saving..." : "Crop & Save"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
