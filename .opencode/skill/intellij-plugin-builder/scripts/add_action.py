import os
import sys

def create_action():
    print("--- IntelliJ Plugin Action Scaffolder ---")
    
    action_id = input("Enter Action ID (e.g., com.example.MyAction): ").strip()
    class_name = input("Enter Class Name (e.g., MyAction): ").strip()
    package_name = input("Enter Package Name (e.g., com.example.actions): ").strip()
    display_name = input("Enter Display Name (e.g., My Action): ").strip()
    
    if not all([action_id, class_name, package_name, display_name]):
        print("Error: All fields are required.")
        return

    # Create directory structure
    base_path = os.path.join("src", "main", "kotlin", *package_name.split("."))
    os.makedirs(base_path, exist_ok=True)
    
    file_path = os.path.join(base_path, f"{class_name}.kt")
    
    content = f"""package {package_name}

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class {class_name} : AnAction() {{
    override fun actionPerformed(e: AnActionEvent) {{
        // TODO: insert action logic here
        Messages.showMessageDialog(
            e.project,
            "Hello from {display_name}!",
            "{display_name}",
            Messages.getInformationIcon()
        )
    }}
}}
"""
    
    with open(file_path, "w") as f:
        f.write(content)
        
    print(f"\n✅ Created Action class at: {file_path}")
    
    print("\n⚠️  Now add this to your plugin.xml inside <actions>:")
    print(f"""
    <action id="{action_id}" class="{package_name}.{class_name}" text="{display_name}" description="{display_name} Action">
        <add-to-group group-id="ToolsMenu" anchor="last"/>
    </action>
    """)

if __name__ == "__main__":
    create_action()
