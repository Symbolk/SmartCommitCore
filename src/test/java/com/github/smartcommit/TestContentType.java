package com.github.smartcommit;

import com.github.smartcommit.model.constant.ContentType;
import com.github.smartcommit.util.Utils;
import org.apache.log4j.PropertyConfigurator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TestContentType {
  @BeforeAll
  public static void setUpBeforeAll() {
    PropertyConfigurator.configure("log4j.properties");
  }

  @Test
  public void testAllImport() {
    List<String> codeLines = new ArrayList<>();
    codeLines.add("import org.apache.log4j.PropertyConfigurator;");
    codeLines.add("import org.junit.jupiter.api.BeforeAll;");
    codeLines.add("import org.junit.jupiter.api.Test;");
    ContentType type = Utils.checkContentType(codeLines);
    assertThat(type).isEqualTo(ContentType.IMPORT);
  }

  @Test
  public void testAllComment() {
    List<String> codeLines = new ArrayList<>();
    codeLines.add("/*import org.apache.log4j.PropertyConfigurator;");
    codeLines.add("*import org.junit.jupiter.api.BeforeAll;");
    codeLines.add("*/");
    codeLines.add("// jjjj");
    ContentType type = Utils.checkContentType(codeLines);
    assertThat(type).isEqualTo(ContentType.COMMENT);
  }

  @Test
  public void testMixed() {
    List<String> codeLines = new ArrayList<>();
    codeLines.add("import org.apache.log4j.PropertyConfigurator;");
    codeLines.add("import org.junit.jupiter.api.BeforeAll;");
    codeLines.add("public class TestContentType {\n");
    codeLines.add("// jjjj");
    ContentType type = Utils.checkContentType(codeLines);
    assertThat(type).isEqualTo(ContentType.CODE);
  }
}

