"""
简单的设备激活服务器（无依赖纯 Python）
"""

import json
from http.server import HTTPServer, BaseHTTPRequestHandler

# 激活码列表
ACTIVATION_CODES = {
    "ABC123": False,
    "DEF456": False,
    "GHI789": False,
    "JKL012": False,
    "MNO345": False,
    "PQR678": False,
    "STU901": False,
    "VWX234": False,
    "YZA567": False,
    "BCD890": False,
    "EFG123": False,
    "HIJ456": False,
    "KLM789": False,
    "NOP012": False,
    "QRS345": False,
}

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/status' or self.path == '/':
            # 显示激活码状态页面
            html = """<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>激活码管理</title>
    <style>
        body { font-family: Arial; padding: 20px; }
        table { border-collapse: collapse; width: 100%; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        th { background-color: #4CAF50; color: white; }
        .used { background-color: #ffcccc; }
        .unused { background-color: #ccffcc; }
    </style>
</head>
<body>
    <h1>设备激活码状态</h1>
    <table>
        <tr><th>激活码</th><th>状态</th></tr>
"""
            for code, used in ACTIVATION_CODES.items():
                status = "已使用" if used else "未使用"
                cls = "used" if used else "unused"
                html += f'        <tr class="{cls}"><td>{code}</td><td>{status}</td></tr>\n'
            
            html += """    </table>
    <p>总计: """ + str(len(ACTIVATION_CODES)) + """ 个激活码，已用: """ + str(sum(1 for v in ACTIVATION_CODES.values() if v)) + """</p>
</body>
</html>"""
            
            self.send_response(200)
            self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.send_header('Content-Length', len(html.encode('utf-8')))
            self.end_headers()
            self.wfile.write(html.encode('utf-8'))
        else:
            self.send_error(404)
    
    def do_POST(self):
        if self.path == '/api/device/activate':
            content_length = int(self.headers.get('Content-Length', 0))
            body = self.rfile.read(content_length).decode('utf-8')
            
            try:
                data = json.loads(body)
                activation_code = data.get('activationCode', '').strip()
            except:
                self.send_error(400, "Invalid JSON")
                return
            
            print("\n" + "="*50)
            print("设备请求激活!")
            print("="*50)
            print(f"激活码: {activation_code}")
            print("="*50)
            
            # 检查激活码
            if activation_code not in ACTIVATION_CODES:
                print("✗ 激活码无效\n")
                self.send_json({"code": 1001, "message": "激活码无效", "data": {"activated": False}})
            elif ACTIVATION_CODES[activation_code]:
                print("✗ 激活码已被使用\n")
                self.send_json({"code": 1002, "message": "激活码已被使用", "data": {"activated": False}})
            else:
                ACTIVATION_CODES[activation_code] = True
                print("✓ 激活成功!\n")
                print("=" * 50)
                print("激活码状态:")
                used = [k for k, v in ACTIVATION_CODES.items() if v]
                unused = [k for k, v in ACTIVATION_CODES.items() if not v]
                print(f"已使用 ({len(used)}): {used}")
                print(f"未使用 ({len(unused)}): {unused}")
                print("=" * 50 + "\n")
                self.send_json({"code": 0, "message": "激活成功", "data": {"activated": True}})
        else:
            self.send_error(404)
    
    def send_json(self, data):
        response = json.dumps(data).encode('utf-8')
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', len(response))
        self.end_headers()
        self.wfile.write(response)
    
    def log_message(self, format, *args):
        pass  # 禁用日志

if __name__ == '__main__':
    port = 80
    server = HTTPServer(('0.0.0.0', port), Handler)
    print("\n" + "="*50)
    print("设备激活服务器")
    print("="*50)
    print(f"端口: {port}")
    print(f"共 {len(ACTIVATION_CODES)} 个激活码")
    print("="*50)
    print("\n使用方法:")
    print("1. 启动服务器")
    print("2. 在设备的登录页面点击'激活设备'")
    print("3. 输入激活服务器地址: http://<电脑IP>:8080")
    print("4. 输入激活码进行激活")
    print("="*50 + "\n")
    
    server.serve_forever()
