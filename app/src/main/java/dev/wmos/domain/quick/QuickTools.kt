package dev.wmos.domain.quick

/**
 * Tiny arithmetic evaluator for the offline calculator.
 *
 * No reflection, no `eval`, no arbitrary code: a hand-written recursive-descent parser that
 * accepts only numbers, `+ - * / % ( )` and unary minus.
 */
object SafeExpression {
    fun evaluate(input: String): Result<Double> {
        val parser = Parser(input)
        return try {
            val value = parser.parseExpression()
            parser.skipSpaces()
            if (!parser.atEnd()) {
                Result.failure(IllegalArgumentException("Лишние символы в выражении"))
            } else if (value.isNaN() || value.isInfinite()) {
                Result.failure(IllegalArgumentException("Результат не является числом"))
            } else {
                Result.success(value)
            }
        } catch (error: IllegalArgumentException) {
            Result.failure(error)
        }
    }

    private class Parser(private val text: String) {
        private var index = 0

        fun atEnd(): Boolean = index >= text.length

        fun skipSpaces() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                skipSpaces()
                if (atEnd()) return value
                when (text[index]) {
                    '+' -> {
                        index++
                        value += parseTerm()
                    }

                    '-' -> {
                        index++
                        value -= parseTerm()
                    }

                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parseFactor()
            while (true) {
                skipSpaces()
                if (atEnd()) return value
                when (text[index]) {
                    '*' -> {
                        index++
                        value *= parseFactor()
                    }

                    '/' -> {
                        index++
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw IllegalArgumentException("Деление на ноль")
                        value /= divisor
                    }

                    '%' -> {
                        index++
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw IllegalArgumentException("Деление на ноль")
                        value %= divisor
                    }

                    else -> return value
                }
            }
        }

        private fun parseFactor(): Double {
            skipSpaces()
            if (atEnd()) throw IllegalArgumentException("Выражение не закончено")
            if (text[index] == '-') {
                index++
                return -parseFactor()
            }
            if (text[index] == '+') {
                index++
                return parseFactor()
            }
            if (text[index] == '(') {
                index++
                val value = parseExpression()
                skipSpaces()
                if (atEnd() || text[index] != ')') throw IllegalArgumentException("Не закрыта скобка")
                index++
                return value
            }
            val start = index
            while (index < text.length && (text[index].isDigit() || text[index] == '.' || text[index] == ',')) {
                index++
            }
            if (start == index) throw IllegalArgumentException("Ожидалось число")
            val raw = text.substring(start, index).replace(',', '.')
            return raw.toDoubleOrNull() ?: throw IllegalArgumentException("Некорректное число")
        }
    }
}

object PasswordGenerator {
    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "!@#\$%^&*()-_=+[]{}"

    /**
     * Uses [java.security.SecureRandom]; the result is never sent to any AI provider and never logged.
     * Guarantees at least one character from every selected class.
     */
    fun generate(
        length: Int,
        useDigits: Boolean = true,
        useSymbols: Boolean = true,
        useUpper: Boolean = true,
    ): String {
        val alphabet = buildString {
            append(LOWER)
            if (useUpper) append(UPPER)
            if (useDigits) append(DIGITS)
            if (useSymbols) append(SYMBOLS)
        }
        if (alphabet.isEmpty()) return ""
        val random = java.security.SecureRandom()
        val size = length.coerceIn(8, 128)
        val chars = CharArray(size) { alphabet[random.nextInt(alphabet.length)] }
        // Enforcement pass so the promise "contains a digit" is true, not probable.
        val required = buildList {
            if (useUpper) add(UPPER)
            if (useDigits) add(DIGITS)
            if (useSymbols) add(SYMBOLS)
        }
        required.forEachIndexed { position, pool ->
            if (position < chars.size) {
                chars[position] = pool[random.nextInt(pool.length)]
            }
        }
        return chars.concatToString()
    }

    fun strength(length: Int, digits: Boolean, symbols: Boolean, upper: Boolean): String {
        var entropy = length * kotlin.math.log2(26.0)
        if (upper) entropy += length * 1.0
        if (digits) entropy += length * 1.2
        if (symbols) entropy += length * 1.6
        return when {
            entropy < 45 -> "Слабый"
            entropy < 70 -> "Средний"
            entropy < 100 -> "Сильный"
            else -> "Очень сильный"
        }
    }
}

object UnitConverter {
    enum class Kind(val label: String) { LENGTH("Длина"), MASS("Масса"), TEMPERATURE("Температура") }

    data class Unit(val id: String, val label: String, val factor: Double)

    val units = mapOf(
        Kind.LENGTH to listOf(
            Unit("m", "метр", 1.0),
            Unit("km", "километр", 1000.0),
            Unit("cm", "сантиметр", 0.01),
            Unit("mm", "миллиметр", 0.001),
            Unit("mi", "миля", 1609.344),
            Unit("ft", "фут", 0.3048),
        ),
        Kind.MASS to listOf(
            Unit("kg", "килограмм", 1.0),
            Unit("g", "грамм", 0.001),
            Unit("t", "тонна", 1000.0),
            Unit("lb", "фунт", 0.45359237),
            Unit("oz", "унция", 0.028349523125),
        ),
        Kind.TEMPERATURE to listOf(
            Unit("c", "Цельсий", 1.0),
            Unit("f", "Фаренгейт", 1.0),
            Unit("k", "Кельвин", 1.0),
        ),
    )

    fun convert(kind: Kind, from: String, to: String, value: Double): Double {
        if (kind == Kind.TEMPERATURE) {
            val celsius = when (from) {
                "f" -> (value - 32) * 5 / 9
                "k" -> value - 273.15
                else -> value
            }
            return when (to) {
                "f" -> celsius * 9 / 5 + 32
                "k" -> celsius + 273.15
                else -> celsius
            }
        }
        val list = units.getValue(kind)
        val fromFactor = list.firstOrNull { it.id == from }?.factor ?: 1.0
        val toFactor = list.firstOrNull { it.id == to }?.factor ?: 1.0
        return value * fromFactor / toFactor
    }
}
