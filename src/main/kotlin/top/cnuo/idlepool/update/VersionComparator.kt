package top.cnuo.idlepool.update

import java.util.Locale

object VersionComparator {
    @JvmStatic
    fun compare(left: String?, right: String?): Int {
        val first = parse(left)
        val second = parse(right)
        repeat(maxOf(first.core.size, second.core.size)) { index ->
            val result = first.core.getOrElse(index) { 0 }.compareTo(second.core.getOrElse(index) { 0 })
            if (result != 0) return result
        }
        if (first.pre.isEmpty() || second.pre.isEmpty()) {
            return when {
                first.pre.isEmpty() && second.pre.isEmpty() -> 0
                first.pre.isEmpty() -> 1
                else -> -1
            }
        }
        repeat(maxOf(first.pre.size, second.pre.size)) { index ->
            if (index >= first.pre.size) return -1
            if (index >= second.pre.size) return 1
            val result = compareIdentifier(first.pre[index], second.pre[index])
            if (result != 0) return result
        }
        return 0
    }

    private fun parse(input: String?): ParsedVersion {
        require(!input.isNullOrBlank()) { "版本号不能为空" }
        val normalized = input.trim().lowercase(Locale.ROOT).removePrefix("v").substringBefore('+')
        val pieces = normalized.split('-', limit = 2)
        val core = pieces[0].split('.').map {
            require(it.all(Char::isDigit)) { "无法识别版本号：$input" }
            it.toInt()
        }
        return ParsedVersion(core, pieces.getOrNull(1)?.split('.', '-') ?: emptyList())
    }

    private fun compareIdentifier(left: String, right: String): Int {
        val leftNumber = left.toIntOrNull()
        val rightNumber = right.toIntOrNull()
        return when {
            leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
            leftNumber != null -> -1
            rightNumber != null -> 1
            else -> left.compareTo(right)
        }
    }

    private data class ParsedVersion(val core: List<Int>, val pre: List<String>)
}
