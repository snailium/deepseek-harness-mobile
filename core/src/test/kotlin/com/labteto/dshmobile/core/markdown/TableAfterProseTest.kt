package com.labteto.dshmobile.core.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A table that starts immediately under the line introducing it, with no blank line between.
 *
 * Reported from a real reply: "same model for comparison:" followed by a table rendered as raw
 * pipe rows, because the paragraph loop swallowed the whole table.
 */
class TableAfterProseTest {

    @Test
    fun tableDirectlyUnderProseIsATable() {
        val blocks = parseMarkdown(
            "对比参照（同一模型）：\n" +
                "| 硬件 | 整数点积 | tg128 |\n" +
                "|--- |--- |--- |\n" +
                "| Arc B390 | 硬件 | 13.16 t/s |\n",
        )
        assertEquals(2, blocks.size)
        assertTrue("first block is the prose", blocks[0] is MdBlock.Paragraph)
        val table = blocks[1] as MdBlock.Table
        assertEquals(listOf("硬件", "整数点积", "tg128"), table.header)
        assertEquals(1, table.rows.size)
        assertEquals(listOf("Arc B390", "硬件", "13.16 t/s"), table.rows[0])
    }

    @Test
    fun tableDirectlyUnderHeadingIsATable() {
        val blocks = parseMarkdown(
            "### 硬件身份\n| 项 | 值 |\n|---|---|\n| GPU | AMD BC-250 |\n",
        )
        assertTrue(blocks.any { it is MdBlock.Table })
    }

    @Test
    fun tableUnderProseKeepsTheProseBeforeIt() {
        val blocks = parseMarkdown(
            "# Title\n\nIntro line.\n| a | b |\n| - | - |\n| 1 | 2 |\n",
        )
        val table = blocks.filterIsInstance<MdBlock.Table>().single()
        assertEquals(listOf("a", "b"), table.header)
        val prose = blocks.filterIsInstance<MdBlock.Paragraph>().single()
        assertEquals(listOf("Intro line."), prose.lines)
    }

    @Test
    fun pipeRowsWithNoSeparatorStayProseEvenAfterText() {
        // The look-ahead must not turn ordinary pipe-bearing prose into a table.
        val blocks = parseMarkdown("Some text\n| not a table | at all |\n")
        assertTrue(blocks.none { it is MdBlock.Table })
    }
}
