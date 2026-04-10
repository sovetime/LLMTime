文件上传测试
更专业的做法：造数据
光靠真实文件不够，边界场景才是重点：
java// 用 Faker 或手写生成各种 case
- 0 字节空文件
- 超大文件（接近限制边界，如 99.9MB vs 100MB）
- 文件名含特殊字符：中文、空格、../../../etc/passwd
- 扩展名欺骗：把 .exe 改成 .jpg 上传
- MIME type 与扩展名不一致
- 损坏文件（截断的 PDF/图片）
- 超长文件名（255+ 字符）
  这些场景开放文件集根本覆盖不到，必须自己构造。

性能测试文件
如果要测吞吐、并发上传：
bash# Linux 快速生成指定大小文件
dd if=/dev/urandom of=test_10mb.bin bs=1M count=10
dd if=/dev/zero of=test_100mb.bin bs=1M count=100

# 生成随机内容 PDF（用 Python）
pip install reportlab faker
pythonfrom reportlab.pdfgen import canvas
from faker import Faker

fake = Faker('zh_CN')
c = canvas.Canvas("test.pdf")
for i in range(100):
c.drawString(100, 800 - i*8, fake.text(50))
c.save()