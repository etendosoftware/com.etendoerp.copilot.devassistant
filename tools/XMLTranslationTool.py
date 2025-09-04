import os
import xml.etree.ElementTree as ET
from typing import Dict, Type

from copilot.core.tool_input import ToolField, ToolInput
from copilot.core.tool_wrapper import ToolWrapper
from copilot.core.utils.models import get_openai_client


class XMLTranslationToolInput(ToolInput):
    """
    Input schema for the XMLTranslationTool.

    Attributes:
        relative_path (str): Relative path to the directory containing XML files to
         translate.
    """

    relative_path: str = ToolField(description="relative path to the XML files")


class XMLTranslationTool(ToolWrapper):
    """
    A tool for translating XML files from one language to another using OpenAI's API.

    This tool walks through a directory structure, identifies XML files that need
    translation, and translates untranslated text elements within them. It uses the
    language specified in the XML file's attributes and translates content in the
    context of ERP software.
    """

    name: str = "XMLTranslationTool"
    description: str = (
        "This is a tool that receives a relative path and directly "
        "translates the content of XML from one "
        "language to another, specified within the xml"
    )
    args_schema: Type[ToolInput] = XMLTranslationToolInput

    def run(self, input_params: Dict, *args, **kwargs):
        """
        Execute the XML translation process for files in the specified relative path.

        Args:
            input_params (Dict): Dictionary containing 'relative_path' key with the
                relative path to the directory containing XML files to translate.
            *args: Variable length argument list.
            **kwargs: Arbitrary keyword arguments.

        Returns:
            Dict or str: Dictionary with 'translated_files_paths' containing list of
                translated file paths, or a string message if no files were translated
                or path not found.
        """
        relative_path = input_params.get("relative_path")
        translated_files_paths = []
        script_directory = os.path.dirname(os.path.abspath(__file__))
        first_level_up = os.path.dirname(script_directory)
        second_level_up = os.path.dirname(first_level_up)
        parent_directory = os.path.dirname(second_level_up)
        absolute_path = os.path.join(parent_directory, relative_path)
        reference_data_path = absolute_path

        if not os.path.exists(reference_data_path):
            return f"The mentioned path was not found  {reference_data_path}."

        for dirpath, dirnames, filenames in os.walk(reference_data_path):
            if "/.git/" in dirpath:
                continue

            xml_files = [f for f in filenames if f.endswith(".xml")]
            for xml_file in xml_files:
                filepath = os.path.join(dirpath, xml_file)
                if self.is_already_translated(filepath):
                    continue
                translated_file_path = self.translate_xml_file(filepath)
                if translated_file_path:
                    translated_files_paths.append(translated_file_path)

        if not translated_files_paths:
            return "The files are already translated"
        return {"translated_files_paths": translated_files_paths}

    def get_language_name(self, iso_code):
        """
        Convert an ISO language code to the full language name.

        Args:
            iso_code (str): ISO language code (e.g., 'es_ES', 'en_US').

        Returns:
            str or None: Full language name if found, None otherwise.
        """
        import pycountry

        language_part = iso_code.split("_")[0]
        language = pycountry.languages.get(alpha_2=language_part)
        if language:
            return language.name
        return None

    def split_xml_into_segments(self, content, max_tokens):
        """
        Split XML content into segments based on token limits for translation.

        Args:
            content (str): The XML content as a string.
            max_tokens (int): Maximum number of tokens allowed per segment.

        Returns:
            List[str]: List of XML segments as strings.
        """
        root = ET.fromstring(content)
        segments = []
        current_segment = ET.Element(root.tag)

        for child in root:
            if (
                ET.tostring(current_segment).strip().decode()
                == f"<{root.tag}></{root.tag}>"
            ):
                continue

            if (
                len(ET.tostring(current_segment).strip().decode())
                + len(ET.tostring(child))
                + 2
                <= max_tokens
            ):
                current_segment.append(child)
            else:
                segments.append(ET.tostring(current_segment).strip().decode())
                current_segment = ET.Element(root.tag)
                current_segment.append(child)

        if current_segment:
            segments.append(ET.tostring(current_segment).strip().decode())

        return segments

    def translate_xml_file(self, filepath):
        """
        Translate untranslated text elements in an XML file using OpenAI API.

        Args:
            filepath (str): Absolute path to the XML file to translate.

        Returns:
            str or None: Success message with file path if translation occurred,
                None if no untranslated elements were found.
        """
        client = get_openai_client()
        business_requirement = os.getenv("BUSINESS_TOPIC", "ERP")
        model = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
        with open(filepath, "r") as file:
            first_line = file.readline().strip()
            content = file.read()
            root = ET.fromstring(content)

            language_attr = root.attrib.get("language", "es_ES")
            target_language = self.get_language_name(language_attr)
            if not target_language:
                target_language = "English"
            value_elements = []
            self.find_untranslated(root, value_elements)

            if not value_elements:
                return None

            original_texts = [
                value.text
                for value in value_elements
                if value.text and value.text.strip()
            ]
            prompt_texts = "\n".join(original_texts)
            prompt = f"""
            ---
            Translate the following texts from English to {target_language} in the
            context of {business_requirement} software:
            {prompt_texts}
            """

            messages = [{"role": "system", "content": prompt}]
            response = client.chat.completions.create(
                model=model, messages=messages, max_tokens=2000, temperature=0
            )

            translations = response.choices[0].message.content.strip().split("\n")

            for i, value in enumerate(value_elements):
                if i < len(translations):
                    value.text = translations[i].strip()
                    value.set("isTrl", "Y")

            translated_text = ET.tostring(root, encoding="unicode")

            with open(filepath, "w", encoding="utf-8") as file:
                file.write(f"{first_line}\n")
                file.write(translated_text)

            return f"Successfully translated file {filepath}."

    def find_untranslated(self, root, value_elements):
        """
        Find untranslated value elements in the XML tree.

        Args:
            root: Root element of the XML tree.
            value_elements (List): List to append untranslated value elements to.
        """
        for child in root:
            if child.attrib.get("trl") == "Y":
                continue

            values = child.findall("value")
            for value in values:
                if value.get("isTrl", "N") == "Y":
                    continue
                original_text = value.get("original", "").strip()
                if not original_text:
                    continue
                value_elements.append(value)
                is_trl = "Y"
                child.attrib["trl"] = is_trl

    def is_already_translated(self, filepath):
        """
        Check if an XML file has already been fully translated.

        Args:
            filepath (str): Path to the XML file to check.

        Returns:
            bool: True if all value elements are translated, False otherwise.
        """
        try:
            with open(filepath, "r") as file:
                root = ET.parse(file).getroot()
            for child in root:
                values = child.findall("value")
                for value in values:
                    if value.get("isTrl", "N") == "N":
                        return False
            return True
        except ET.ParseError:
            return False
