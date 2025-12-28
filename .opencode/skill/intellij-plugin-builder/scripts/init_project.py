import os
import sys
import shutil
import argparse

def main():
    parser = argparse.ArgumentParser(description="Initialize a new IntelliJ Plugin project.")
    parser.add_argument("project_name", help="Name of the project (e.g., MyAwesomePlugin)")
    parser.add_argument("package_name", help="Base package name (e.g., com.example.myplugin)")
    parser.add_argument("--target-dir", default=".", help="Directory to create the project in (default: current dir)")
    
    args = parser.parse_args()
    
    project_name = args.project_name
    package_name = args.package_name
    target_dir = os.path.abspath(os.path.join(args.target_dir, project_name))
    
    # Determine paths
    script_dir = os.path.dirname(os.path.abspath(__file__))
    skill_dir = os.path.dirname(script_dir)
    assets_dir = os.path.join(skill_dir, "assets")
    
    print(f"Initializing project '{project_name}' in '{target_dir}'...")
    
    # Create directory structure
    src_main_kotlin = os.path.join(target_dir, "src", "main", "kotlin", *package_name.split("."))
    src_main_resources = os.path.join(target_dir, "src", "main", "resources", "META-INF")
    
    os.makedirs(src_main_kotlin, exist_ok=True)
    os.makedirs(src_main_resources, exist_ok=True)
    
    # Copy and template files
    
    # 1. settings.gradle.kts
    with open(os.path.join(assets_dir, "settings.gradle.kts"), "r") as f:
        content = f.read()
    content = content.replace('rootProject.name = "my-intellij-plugin"', f'rootProject.name = "{project_name}"')
    with open(os.path.join(target_dir, "settings.gradle.kts"), "w") as f:
        f.write(content)
        
    # 2. build.gradle.kts
    with open(os.path.join(assets_dir, "build.gradle.kts"), "r") as f:
        content = f.read()
    content = content.replace('group = "com.example"', f'group = "{package_name}"')
    with open(os.path.join(target_dir, "build.gradle.kts"), "w") as f:
        f.write(content)
        
    # 3. gradle.properties
    shutil.copy(os.path.join(assets_dir, "gradle.properties"), os.path.join(target_dir, "gradle.properties"))
    
    # 4. .gitignore
    shutil.copy(os.path.join(assets_dir, ".gitignore"), os.path.join(target_dir, ".gitignore"))
    
    # 5. plugin.xml
    with open(os.path.join(assets_dir, "plugin.xml"), "r") as f:
        content = f.read()
    
    plugin_id = package_name
    content = content.replace('<id>com.example.plugin-id</id>', f'<id>{plugin_id}</id>')
    content = content.replace('<name>Plugin Display Name</name>', f'<name>{project_name}</name>')
    
    with open(os.path.join(src_main_resources, "plugin.xml"), "w") as f:
        f.write(content)
        
    print(f"✅ Project initialized successfully!")
    print(f"   Location: {target_dir}")
    print(f"   Package:  {package_name}")
    print("\nNext steps:")
    print(f"1. cd {project_name}")
    print("2. ./gradlew runIde")

if __name__ == "__main__":
    main()
