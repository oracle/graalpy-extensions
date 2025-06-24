# Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
import mypy
import os
from mypy.build import build
from mypy.main import process_options
from mypy.nodes import MypyFile
from mypy.fscache import FileSystemCache
from mypy.fixup import fixup_module

mypy_cache_dir = 'mypy_cache'

def set_mypy_cache_folder(folder_path: str) -> None:
    global mypy_cache_dir
    mypy_cache_dir = folder_path

def extract_type_info(file_paths: list[str]):
    sources, options = process_options(file_paths)
    

    options.incremental = False
    options.fine_grained_incremental=False
    options.export_types = True
    options.follow_imports="silent"
    options.show_traceback = True
    options.preserve_asts = True
    options.include_docstrings=True
    options.strict_optional=False
    options.skip_version_check=True
    options.cache_dir= mypy_cache_dir

    
    
    fscache = FileSystemCache()

    result =  build(sources, options=options, fscache = fscache)
#    print(f"{fscache=}")
#    print(f"{result.files}");
#    print(f"{result.types}");
#    print(f"{result.used_cache}");
    return result
    
def getNodesForFile(file_path: str) -> mypy.nodes.MypyFile :
    result = extract_type_info([file_path])
    absPath = os.path.abspath(file_path)
    for key, value in result.files.items():
        if value.path == absPath:
            return value

def serialize_result(paths: list[str], cache_file_path:str)->dict[str, MypyFile]:
    import json
    import time

    result = extract_type_info(paths)

    ast_dict = {file: result.files[file].serialize() for file in result.files}
    
    directory = os.path.dirname(cache_file_path)
    if directory and not os.path.exists(directory):
        os.makedirs(directory)

    with open(cache_file_path, 'w') as f:
        json.dump(ast_dict, f, default=str, indent=2)
    
    return result.files

def serialize_mypyfile(mypyfile, output_file:str) -> None:  
    import json
    d = {mypyfile['name']: mypyfile}
    with open(output_file, 'w') as f:
        json.dump(d, f, default=str, indent=2)


def serialize_mypyfile(mypyfile:MypyFile, output_file:str) -> None:  
    import json
    d = {mypyfile.fullname: mypyfile.serialize()} 
    with open(output_file, 'w') as f:
        json.dump(d, f, default=str, indent=2)

def serialize_mypyFile_toStr(mypyfile: MypyFile) -> str:
    import json
    d = {mypyfile.fullname: mypyfile.serialize()} 
    return json.dumps(d, default=str, indent=2)

def serialize_ast(ast:str, output_file:str)->None:
    import json
    with open(output_file, 'w') as f:
        json.dump(ast.serialize(), f)
        
def load_ast(json_filename):
    import json
    import time
    
    with open(json_filename, 'r') as f:
        ast_data = json.load(f)   

    start_time = time.time()
    mypyFile = mypy.nodes.MypyFile.deserialize(ast_data)
    end_time = time.time()
    print(f'Deserialization took: {(end_time - start_time)} seconds')
    start_time = end_time
#    fixup_module(mypyFile, dict(), False)
#    end_time = time.time()
#    print(f'Fixing took: {(end_time - start_time)} seconds')
    return mypyFile

def create_mypyfile(data:str)->dict[str, MypyFile]:
    import json
    ast_data = json.loads(data)
    ast_nodes = {file: mypy.nodes.MypyFile.deserialize(ast) for file, ast in ast_data.items()}

    for file, mypy_file in ast_nodes.items():
        print(f'Fixing file:  {mypy_file.path}')
        fixup_module(mypy_file, ast_nodes, True)

    return ast_nodes

def load_result(json_filename:str, previous: dict[str, MypyFile] = None) -> dict[str, MypyFile]:
    import json
    import time
    
    with open(json_filename, 'r') as f:
        ast_data = json.load(f)
       
    ast_nodes = {file: mypy.nodes.MypyFile.deserialize(ast) for file, ast in ast_data.items()}

    for file, mypy_file in ast_nodes.items():
        print(f'Fixing file:  {mypy_file.path}')
        if previous :
            print('previous is on the way')
            ast_nodes = {**ast_nodes, **previous}
            print(f'{ast_nodes=}')

        fixup_module(mypy_file, ast_nodes, True)

    return ast_nodes



def extract_docstrings(file_path:str, moduleFQN:str)->dict[str, str]:
    import ast
    with open(file_path, "r", encoding="utf-8") as file:
        source_code = file.read()

    tree = ast.parse(source_code)

    docstrings = {}

    def get_qualified_name(node, parent_name=moduleFQN):
        if isinstance(node, ast.FunctionDef):
            return f"{parent_name}.{node.name}" if parent_name else node.name
        elif isinstance(node, ast.ClassDef):
            return f"{parent_name}.{node.name}" if parent_name else node.name
        elif isinstance(node, ast.Module):
            return moduleFQN
        else:
            return None

    def process_node(node, parent_name=moduleFQN):
        qualified_name = get_qualified_name(node, parent_name)
        if qualified_name :
            docstring = ast.get_docstring(node)
            if docstring:
                docstrings[qualified_name] = docstring

        for child in ast.iter_child_nodes(node):
            process_node(child, qualified_name)

    process_node(tree)

    return docstrings

def test_fn_noArgs():
    print("calling test_fn_noArgs()")

def test_only_posArgs(*args):
    print("calling test_only_posArgs(*args):")
    print(f"    {args=}")

def test_only_kwArgs(**kwargs):
    print("calling test_only_kwArgs(**kwargs):")
    print(f"    {kwargs=}")
    

def test_posArgs_kwArgs(*args, **kwArgs):
    print("calling test_posArgs_kwArgs(*args, **kwArgs):")
    print(f"    {args=}")
    print(f"    {kwArgs=}")
    

def test_pos_posArgs(arg1, *args):
    print("calling test_pos_posArgs(arg1, *args):")
    print(f"    {arg1=}")
    print(f"    {args=}")

def test_pos_kwArgs(arg1, **kwArgs):
    print("calling test_pos_kwArgs(arg1, **kwArgs):")
    print(f"    {arg1=}")
    print(f"    {kwArgs=}")

def test_pos_posArgs_kwArgs(arg1, *args, **kwArgs):
    print("calling test_pos_posArgs_kwArgs(arg1, *args, **kwArgs):")
    print(f"    {arg1=}")
    print(f"    {args=}")
    print(f"    {kwArgs=}")
    
def test_pos3_posArgs_kwArgs(arg1, arg2, arg3, *args, **kwArgs):
    print("calling test_pos_posArgs_kwArgs(arg1, *args, **kwArgs):")
    print(f"    {arg1=}")
    print(f"    {arg2=}")
    print(f"    {arg3=}")
    print(f"    {args=}")
    print(f"    {kwArgs=}")


def test_posArgs_named_args(*args, named1, named2=10):
    print("calling test_posArgs_named_args(*args, named1, named2=10): ")
    print(f"    {args=}")
    print(f"    {named1=}")
    print(f"    {named2=}")
    

def test_posarg(a, b, c):
    print("calling test_posarg(a, b, c):")
    print(f"    {a=}")
    print(f"    {b=}")
    print(f"    {c=}")


def test_kwargsonly2(a, **kwargs):
    print("calling test_kwargsonly2(**kwargs):")
    print(f"    {kwargs=}")
    print(f"    {kwargs['title']=}")

def test_kwargsonly3( a = 1, b = "ahoj"):
    print("calling test_kwargsonly3(a,b):")
    print(f"    {a=}")
    print(f"    {b=}")

if __name__ == "__main__":
    print(__name__)
#    serialize_result("/home/petr/labs/igen/JavaInterfaceGenerator/src/test/resources/testData/testFiles/FunctionTest/function01.py",
#        "/home/petr/labs/igen/JavaInterfaceGenerator/test.json")
#    load_result("/home/petr/labs/igen/JavaInterfaceGenerator/test.json", "function01")
#polyglot.export_value("getAst", extract_type_info)
#polyglot.export_value("getNodesForFile", getNodesForFile)
