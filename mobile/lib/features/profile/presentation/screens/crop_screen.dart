import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:image/image.dart' as img;
import '../../../../core/theme/app_dimens.dart';

class CropScreen extends StatefulWidget {
  const CropScreen({super.key, required this.imageFile});

  final File imageFile;

  @override
  State<CropScreen> createState() => _CropScreenState();
}

class _CropScreenState extends State<CropScreen> {
  final _transformationController = TransformationController();
  bool _loading = true;
  bool _processing = false;
  img.Image? _decodedImage;
  double _displayWidth = 0;
  double _displayHeight = 0;

  static const double viewportSize = 320.0;
  static const double cropSize = 200.0;

  @override
  void initState() {
    super.initState();
    _loadImage();
  }

  @override
  void dispose() {
    _transformationController.dispose();
    super.dispose();
  }

  Future<void> _loadImage() async {
    try {
      final bytes = await widget.imageFile.readAsBytes();
      // Decoded on a background isolate — a 12MP camera shot takes long enough to
      // drop frames on the platform thread.
      final decoded = await compute(_decodeOriented, bytes);
      if (decoded == null) {
        throw Exception('Could not decode image');
      }

      final aspect = decoded.width / decoded.height;
      double dw;
      double dh;

      if (aspect > 1) {
        dh = viewportSize;
        dw = viewportSize * aspect;
      } else {
        dw = viewportSize;
        dh = viewportSize / aspect;
      }

      if (mounted) {
        setState(() {
          _decodedImage = decoded;
          _displayWidth = dw;
          _displayHeight = dh;
          _loading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to load image details: $e')),
        );
        Navigator.of(context).pop();
      }
    }
  }

  Future<void> _save() async {
    if (_decodedImage == null || _processing) return;
    setState(() => _processing = true);

    try {
      final matrix = _transformationController.value;
      final double s = matrix.entry(0, 0);
      final double tx = matrix.entry(0, 3);
      final double ty = matrix.entry(1, 3);

      const double cropLeft = (viewportSize - cropSize) / 2;
      const double cropTop = (viewportSize - cropSize) / 2;

      // Crop coordinates in the displayed image space
      final double cropXInImage = (cropLeft - tx) / s;
      final double cropYInImage = (cropTop - ty) / s;
      final double cropWidthInImage = cropSize / s;
      final double cropHeightInImage = cropSize / s;

      // Map to original image dimensions
      final double originalCropX = cropXInImage * (_decodedImage!.width / _displayWidth);
      final double originalCropY = cropYInImage * (_decodedImage!.height / _displayHeight);
      final double originalCropWidth = cropWidthInImage * (_decodedImage!.width / _displayWidth);
      final double originalCropHeight = cropHeightInImage * (_decodedImage!.height / _displayHeight);

      // Perform crop, resize, and compress on an Isolate to keep UI 60fps
      final cropParams = _CropParams(
        image: _decodedImage!,
        x: originalCropX.round(),
        y: originalCropY.round(),
        w: originalCropWidth.round(),
        h: originalCropHeight.round(),
      );

      final croppedBytes = await compute(_processImage, cropParams);

      if (mounted) {
        Navigator.of(context).pop(croppedBytes);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to crop image: $e')),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _processing = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Crop Avatar'),
        actions: [
          if (!_loading && !_processing)
            IconButton(
              icon: const Icon(Icons.check_rounded),
              onPressed: _save,
            ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : Stack(
              children: [
                Column(
                  children: [
                    const Expanded(child: SizedBox()),
                    // Viewport & InteractiveViewer
                    Center(
                      child: Container(
                        width: viewportSize,
                        height: viewportSize,
                        decoration: BoxDecoration(
                          color: Colors.black,
                          borderRadius: BorderRadius.circular(AppRadii.md),
                          border: Border.all(color: scheme.outlineVariant),
                        ),
                        clipBehavior: Clip.antiAlias,
                        child: Stack(
                          children: [
                            InteractiveViewer(
                              transformationController: _transformationController,
                              minScale: 1.0,
                              maxScale: 4.0,
                              boundaryMargin: const EdgeInsets.all(viewportSize),
                              child: Image.file(
                                widget.imageFile,
                                width: _displayWidth,
                                height: _displayHeight,
                                fit: BoxFit.fill,
                              ),
                            ),
                            // Semi-transparent overlay with circle cutout
                            Positioned.fill(
                              child: IgnorePointer(
                                child: CustomPaint(
                                  painter: CropOverlayPainter(cropSize: cropSize),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: AppSpacing.lg),
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xl),
                      child: Text(
                        'Pinch to zoom and drag to position the avatar inside the circle.',
                        textAlign: TextAlign.center,
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: scheme.onSurfaceVariant,
                        ),
                      ),
                    ),
                    const Expanded(child: SizedBox()),
                  ],
                ),
                if (_processing)
                  Container(
                    color: Colors.black54,
                    child: const Center(
                      child: CircularProgressIndicator(),
                    ),
                  ),
              ],
            ),
    );
  }
}

class CropOverlayPainter extends CustomPainter {
  CropOverlayPainter({required this.cropSize});
  final double cropSize;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()..color = Colors.black.withValues(alpha: 0.6);
    final cropRect = Rect.fromCenter(
      center: Offset(size.width / 2, size.height / 2),
      width: cropSize,
      height: cropSize,
    );

    // Draw background with hole
    canvas.drawPath(
      Path.combine(
        PathOperation.difference,
        Path()..addRect(Rect.fromLTWH(0, 0, size.width, size.height)),
        Path()..addOval(cropRect),
      ),
      paint,
    );

    // Draw white circle outline
    final borderPaint = Paint()
      ..color = Colors.white.withValues(alpha: 0.8)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.5;
    canvas.drawOval(cropRect, borderPaint);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

class _CropParams {
  _CropParams({
    required this.image,
    required this.x,
    required this.y,
    required this.w,
    required this.h,
  });

  final img.Image image;
  final int x;
  final int y;
  final int w;
  final int h;
}

/// Decodes an image and applies its EXIF orientation tag.
///
/// `decodeImage` reads the EXIF block but leaves the pixels in their stored order, so a
/// photo taken with the phone rotated (orientation 6 or 8, the normal case for a portrait
/// shot on Android) decodes on its side. Uploading that produced a sideways avatar, and
/// worse, the crop circle the user positioned did not correspond to the region that was
/// actually cut — the rotation happened after they had already framed the face.
///
/// Baking here rather than at encode time is deliberate: everything downstream — the
/// preview, the crop maths, [_processImage] — then works in display orientation, so what
/// the user frames is what gets uploaded.
img.Image? _decodeOriented(Uint8List bytes) {
  final decoded = img.decodeImage(bytes);
  return decoded == null ? null : img.bakeOrientation(decoded);
}

Uint8List _processImage(_CropParams params) {
  // Ensure we don't crop out of bounds
  int cropX = params.x.clamp(0, params.image.width - 1);
  int cropY = params.y.clamp(0, params.image.height - 1);
  int cropW = params.w.clamp(1, params.image.width - cropX);
  int cropH = params.h.clamp(1, params.image.height - cropY);

  // 1. Crop
  final cropped = img.copyCrop(
    params.image,
    x: cropX,
    y: cropY,
    width: cropW,
    height: cropH,
  );

  // 2. Resize (longest edge <= 1080)
  img.Image resized = cropped;
  if (cropped.width > 1080 || cropped.height > 1080) {
    if (cropped.width > cropped.height) {
      resized = img.copyResize(cropped, width: 1080);
    } else {
      resized = img.copyResize(cropped, height: 1080);
    }
  }

  // 3. Compress to JPEG with quality ~82%
  return Uint8List.fromList(img.encodeJpg(resized, quality: 82));
}
