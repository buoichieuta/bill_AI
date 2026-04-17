"""
🧪 TEST: MERGE ALL BOXES
So sánh: pick 1 field vs merge tất cả fields
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw
sys.path.insert(0, str(Path(__file__).parent))

from src.detector import get_detector, _collect_detection_candidates, _pick_best_single_box

def test_merge_vs_single(image_path):
    """Test: So sánh single box vs merged box"""
    print("\n" + "="*80)
    print("🧪 TEST: SINGLE BOX vs MERGED BOX")
    print("="*80)
    
    # Load ảnh
    if not Path(image_path).exists():
        print(f"❌ File không tồn tại: {image_path}")
        return
    
    image = Image.open(image_path)
    w, h = image.size
    img_area = w * h
    
    print(f"\n📷 Ảnh: {w}×{h} = {img_area:,} pixel")
    
    # Load detector
    model_path = Path(__file__).parent / "best.onnx"
    if not model_path.exists():
        print(f"❌ Model không tồn tại: {model_path}")
        return
    
    detector, _ = get_detector(str(model_path))
    if detector is None:
        print("❌ Không load được model")
        return
    
    print("✅ Model loaded")
    
    # Detect
    print(f"\n🔍 Detecting...")
    pred = detector.predict(source=image.convert("RGB"), verbose=False)
    
    if not pred or pred[0].boxes is None or len(pred[0].boxes) == 0:
        print("❌ Không detect được gì")
        return
    
    boxes = pred[0].boxes
    print(f"✅ Detect được {len(boxes)} fields")
    
    # Collect candidates
    candidates = _collect_detection_candidates(boxes, detector, w, h)
    
    print(f"\n{'─'*80}")
    print("📊 SINGLE BOX METHOD (Cách cũ - Pick 1 field)")
    print(f"{'─'*80}")
    
    # Single box
    picked_box, meta = _pick_best_single_box(candidates)
    if picked_box:
        x1, y1, x2, y2 = picked_box
        bw = x2 - x1
        bh = y2 - y1
        area = (bw * bh) / img_area
        print(f"\n  ✅ Picked: {meta['detector_top_class']}")
        print(f"     Bbox: ({x1}, {y1}) → ({x2}, {y2})")
        print(f"     Size: {bw}×{bh}")
        print(f"     Area ratio: {area*100:.3f}%")
        print(f"     Status: {'✅ OK (> 4%)' if area >= 0.04 else '❌ FAIL (< 4%)'}")
    
    # Merged box
    print(f"\n{'─'*80}")
    print("📦 MERGED BOX METHOD (Cách mới - Merge tất cả fields)")
    print(f"{'─'*80}")
    
    # Find min/max bounds
    all_x1 = [c["x1"] for c in candidates]
    all_y1 = [c["y1"] for c in candidates]
    all_x2 = [c["x2"] for c in candidates]
    all_y2 = [c["y2"] for c in candidates]
    
    merged_x1 = min(all_x1)
    merged_y1 = min(all_y1)
    merged_x2 = max(all_x2)
    merged_y2 = max(all_y2)
    
    # Add padding (5%)
    padding_x = int(w * 0.05)
    padding_y = int(h * 0.05)
    
    merged_x1 = max(0, merged_x1 - padding_x)
    merged_y1 = max(0, merged_y1 - padding_y)
    merged_x2 = min(w, merged_x2 + padding_x)
    merged_y2 = min(h, merged_y2 + padding_y)
    
    merged_bw = merged_x2 - merged_x1
    merged_bh = merged_y2 - merged_y1
    merged_area = (merged_bw * merged_bh) / img_area
    
    print(f"\n  ✅ Merged all {len(candidates)} fields")
    print(f"     Bbox: ({merged_x1}, {merged_y1}) → ({merged_x2}, {merged_y2})")
    print(f"     Size: {merged_bw}×{merged_bh}")
    print(f"     Area ratio: {merged_area*100:.2f}%")
    print(f"     Status: {'✅ OK (> 4%)' if merged_area >= 0.04 else '❌ FAIL (< 4%)'}")
    
    # Comparison
    print(f"\n{'─'*80}")
    print("📈 SO SÁNH")
    print(f"{'─'*80}")
    
    if picked_box:
        x1_p, y1_p, x2_p, y2_p = picked_box
        area_p = ((x2_p - x1_p) * (y2_p - y1_p)) / img_area
        
        print(f"\n  Single box:  {area_p*100:6.3f}% ({'✅' if area_p >= 0.04 else '❌'})")
        print(f"  Merged box:  {merged_area*100:6.2f}% ({'✅' if merged_area >= 0.04 else '❌'})")
        print(f"  Improvement: {(merged_area/max(area_p, 0.0001))*100:6.0f}x lớn hơn")
        
        if merged_area >= 0.04 and area_p < 0.04:
            print(f"\n  ✅ MERGED FIX THE PROBLEM!")
            print(f"     Single: FALLBACK (quá nhỏ)")
            print(f"     Merged: CROP (đủ lớn)")
    
    # Show field breakdown
    print(f"\n{'─'*80}")
    print("🔍 FIELDS DETECTED (Top 10 by size)")
    print(f"{'─'*80}")
    
    cands_sorted = sorted(candidates, key=lambda c: c['area_ratio'], reverse=True)[:10]
    print(f"\n{'Field':<20} {'Size (pixel)':<15} {'Area %':<10} {'Status':<10}")
    print(f"{'─'*80}")
    
    for i, c in enumerate(cands_sorted, 1):
        bw = c['x2'] - c['x1']
        bh = c['y2'] - c['y1']
        area_pct = c['area_ratio'] * 100
        status = '✅' if c['area_ratio'] >= 0.04 else '❌'
        print(f"{c['cls_name']:<20} {bw}×{bh:<10} {area_pct:<9.2f}% {status:<10}")
    
    print(f"\n{'='*80}\n")
    
    # Optional: Visualize
    if False:  # Set to True to save visualization
        img_vis = image.copy()
        draw = ImageDraw.Draw(img_vis, 'RGBA')
        
        # Draw all boxes
        for c in candidates:
            draw.rectangle([c['x1'], c['y1'], c['x2'], c['y2']], 
                          outline='red', width=1)
        
        # Draw merged box
        draw.rectangle([merged_x1, merged_y1, merged_x2, merged_y2], 
                      outline='green', width=3)
        
        # Draw single box
        if picked_box:
            draw.rectangle(picked_box, outline='blue', width=2)
        
        img_vis.save(Path(__file__).parent / "test_boxes_visualization.png")
        print("✅ Saved: test_boxes_visualization.png")

if __name__ == "__main__":
    # Test with the sample invoice image
    test_image = r"c:\Nhap\billlllll\test_invoice.jpg"
    
    if Path(test_image).exists():
        test_merge_vs_single(test_image)
    else:
        print(f"\n❌ Test image không tồn tại: {test_image}")
        print("\nVui lòng chụp ảnh hóa đơn hoặc download ảnh test")
        print("\nHoặc copy file hóa đơn vào:")
        print(f"  {test_image}")
