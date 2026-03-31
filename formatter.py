"""
Format kết quả hóa đơn ra bảng đẹp trên terminal
"""
from tabulate import tabulate


def to_table(data: dict):
    """Hiển thị thông tin hóa đơn dạng bảng"""
    print("\n" + "═" * 60)
    print(f"  🛒  {data.get('SELLER', 'N/A')}")
    if data.get('ADDRESS'):
        print(f"  📍  {data['ADDRESS']}")
    print(f"  🕒  {data.get('TIMESTAMP', 'N/A')}")
    if data.get('INVOICE_NO'):
        print(f"  📋  HD: {data['INVOICE_NO']}")
    print("═" * 60)

    products = data.get("PRODUCTS", [])
    if products:
        product_table = []
        for p in products:
            name = p.get("PRODUCT", "")
            num = p.get("NUM", 1)
            unit_price = p.get("UNIT_PRICE", 0)
            value = p.get("VALUE", 0)
            product_table.append([
                name,
                num,
                f"{unit_price:,.0f} ₫" if unit_price else "—",
                f"{value:,.0f} ₫"
            ])

        print(tabulate(
            product_table,
            headers=["Sản phẩm", "SL", "Đơn giá", "Thành tiền"],
            tablefmt="fancy_grid",
            stralign="left",
            numalign="right"
        ))
    else:
        print("  (Không có sản phẩm)")

    print("═" * 60)
    if data.get('CASH_RECEIVED'):
        print(f"  💵  Tiền khách:  {data['CASH_RECEIVED']:>12,.0f} ₫")
    if data.get('CHANGE'):
        print(f"  ↩️   Tiền thối:   {data['CHANGE']:>12,.0f} ₫")
    print(f"  💰  TỔNG CỘNG:  {data.get('TOTAL_COST', 0):>12,.0f} ₫")
    print("═" * 60 + "\n")