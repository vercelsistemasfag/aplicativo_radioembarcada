package br.com.radioembarcada.programming

import br.com.radioembarcada.model.ProgramItem
import kotlin.random.Random

/** Cada conteúdo sai uma vez por bag; a fronteira nunca repete se há alternativas. */
internal class ShuffleBag(private val random: Random) {
    private var keys = emptySet<String>()
    private val pending = ArrayDeque<String>()
    private var last: String? = null

    val remaining: Int get() = pending.size

    /** Nova revisão descarta o bag pendente, mas conserva a proteção da fronteira. */
    fun invalidate() { pending.clear(); keys = emptySet() }

    fun next(pool: List<ProgramItem>): ProgramItem? {
        val catalog = pool.distinctBy { it.contentId }.associateBy { it.contentId }
        if (catalog.isEmpty()) { pending.clear(); keys = emptySet(); return null }
        if (keys != catalog.keys) { keys = catalog.keys; pending.clear() }
        if (pending.isEmpty()) {
            val order = catalog.keys.shuffled(random).toMutableList()
            if (order.size > 1 && order.first() == last) {
                val replacement = random.nextInt(1, order.size)
                val first = order[0]; order[0] = order[replacement]; order[replacement] = first
            }
            pending.addAll(order)
        }
        last = pending.removeFirst()
        return catalog.getValue(checkNotNull(last))
    }
}
