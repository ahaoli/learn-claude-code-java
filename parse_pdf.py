#!/usr/bin/env python3
"""
PDF文件解析工具
支持多种PDF解析库：PyPDF2, pdfplumber, PyMuPDF
"""

import os
import PyPDF2
import pdfplumber
import fitz  # PyMuPDF

def parse_pdf_with_pypdf2(file_path):
    """使用PyPDF2解析PDF文件"""
    print("\n=== 使用 PyPDF2 解析 ===")
    try:
        with open(file_path, 'rb') as file:
            pdf_reader = PyPDF2.PdfReader(file)
            
            # 获取PDF基本信息
            print(f"文件名: {os.path.basename(file_path)}")
            print(f"总页数: {len(pdf_reader.pages)}")
            
            # 提取每一页的文本
            text_content = ""
            for i, page in enumerate(pdf_reader.pages):
                try:
                    text = page.extract_text()
                    if text:
                        text_content += f"--- 第 {i+1} 页 ---\n{text}\n\n"
                except Exception as e:
                    print(f"第 {i+1} 页提取失败: {e}")
            
            return text_content
    except Exception as e:
        print(f"PyPDF2 解析失败: {e}")
        return None

def parse_pdf_with_pdfplumber(file_path):
    """使用pdfplumber解析PDF文件"""
    print("\n=== 使用 pdfplumber 解析 ===")
    try:
        text_content = ""
        with pdfplumber.open(file_path) as pdf:
            print(f"文件名: {os.path.basename(file_path)}")
            print(f"总页数: {len(pdf.pages)}")
            
            for i, page in enumerate(pdf.pages):
                try:
                    text = page.extract_text()
                    if text:
                        text_content += f"--- 第 {i+1} 页 ---\n{text}\n\n"
                except Exception as e:
                    print(f"第 {i+1} 页提取失败: {e}")
        
        return text_content
    except Exception as e:
        print(f"pdfplumber 解析失败: {e}")
        return None

def parse_pdf_with_pymupdf(file_path):
    """使用PyMuPDF解析PDF文件"""
    print("\n=== 使用 PyMuPDF 解析 ===")
    try:
        doc = fitz.open(file_path)
        print(f"文件名: {os.path.basename(file_path)}")
        print(f"总页数: {len(doc)}")
        
        text_content = ""
        for i, page in enumerate(doc):
            try:
                text = page.get_text()
                if text:
                    text_content += f"--- 第 {i+1} 页 ---\n{text}\n\n"
            except Exception as e:
                print(f"第 {i+1} 页提取失败: {e}")
        
        doc.close()
        return text_content
    except Exception as e:
        print(f"PyMuPDF 解析失败: {e}")
        return None

def get_pdf_info(file_path):
    """获取PDF文件基本信息"""
    try:
        with open(file_path, 'rb') as file:
            pdf_reader = PyPDF2.PdfReader(file)
            
            info = {
                'filename': os.path.basename(file_path),
                'page_count': len(pdf_reader.pages),
                'metadata': pdf_reader.metadata
            }
            
            print("\n=== PDF 基本信息 ===")
            print(f"文件名: {info['filename']}")
            print(f"页数: {info['page_count']}")
            print("元数据:")
            for key, value in info['metadata'].items():
                if value:
                    print(f"  {key}: {value}")
            
            return info
    except Exception as e:
        print(f"获取PDF信息失败: {e}")
        return None

def main():
    # 查找当前目录下的PDF文件
    pdf_files = [f for f in os.listdir('.') if f.lower().endswith('.pdf')]
    
    if not pdf_files:
        print("当前目录下没有找到PDF文件")
        return
    
    print(f"找到 {len(pdf_files)} 个PDF文件:")
    for i, pdf_file in enumerate(pdf_files, 1):
        print(f"{i}. {pdf_file}")
    
    # 如果只有一个PDF文件，直接解析
    if len(pdf_files) == 1:
        pdf_file = pdf_files[0]
        print(f"\n开始解析文件: {pdf_file}")
        
        # 获取基本信息
        get_pdf_info(pdf_file)
        
        # 使用三种方法解析
        results = []
        
        # PyPDF2
        result1 = parse_pdf_with_pypdf2(pdf_file)
        if result1:
            results.append(("PyPDF2", result1))
        
        # pdfplumber
        result2 = parse_pdf_with_pdfplumber(pdf_file)
        if result2:
            results.append(("pdfplumber", result2))
        
        # PyMuPDF
        result3 = parse_pdf_with_pymupdf(pdf_file)
        if result3:
            results.append(("PyMuPDF", result3))
        
        # 保存结果到文件
        if results:
            output_file = f"{os.path.splitext(pdf_file)[0]}_解析结果.txt"
            with open(output_file, 'w', encoding='utf-8') as f:
                f.write(f"PDF文件解析结果\n")
                f.write(f"原文件: {pdf_file}\n")
                f.write(f"解析时间: {os.popen('date').read().strip()}\n")
                f.write("="*50 + "\n\n")
                
                for method, content in results:
                    f.write(f"使用 {method} 解析结果:\n")
                    f.write(content)
                    f.write("\n" + "="*50 + "\n\n")
            
            print(f"\n解析结果已保存到: {output_file}")
        else:
            print("所有解析方法都失败了")
    
    else:
        print("请指定要解析的PDF文件")

if __name__ == "__main__":
    main()