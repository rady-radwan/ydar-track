package com.rady.worktracker.data

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random

/*
 * Encouraging lines — "Build yourself": you are the construction project.
 */

val Quotes: List<String> = listOf(
    // Build yourself
    "You are your greatest project. Keep building.",
    "Every hour is another brick in your future.",
    "Great things are built one day at a time.",
    "The person you want to become is under construction.",
    "Build yourself into someone you're proud of.",
    "Strong foundations are built through daily effort.",
    "Your future is built by what you do today.",
    "Every great achievement started with a single step.",
    "You are both the architect and the builder of your life.",
    "A masterpiece takes time. So do you.",
    // Discipline & hard work
    "Don't just count the hours. Make the hours count.",
    "The work you put in today will speak for you tomorrow.",
    "Your goals won't build themselves.",
    "Show up. Put in the work. Repeat.",
    "Discipline builds what motivation starts.",
    "One more hour. One step closer.",
    "The future belongs to those who keep showing up.",
    "Every focused minute is an investment in yourself.",
    "You don't need to feel ready. You just need to begin.",
    "Be proud of your progress, but never stop growing.",
    // Progress & consistency
    "Small progress is still progress.",
    "A little better every day adds up.",
    "Today's effort is tomorrow's foundation.",
    "Consistency turns ordinary days into extraordinary results.",
    "You don't have to be perfect. Just keep building.",
    "Every session moves you closer to your vision.",
    "Progress is made, not found.",
    "The strongest structures are built brick by brick.",
    "Your only competition is who you were yesterday.",
    "Keep going. You're becoming someone stronger.",
    // Short & powerful
    "Make today count.",
    "Your time. Your future.",
    "Build. Grow. Repeat.",
    "Become your best work.",
    "Keep building.",
    "Earn your progress.",
    "Another day. Another brick.",
    "Create your future.",
    "Your effort matters.",
    "Greatness takes work.",
    // Foundations & consistency
    "Great structures aren't built in a day — they're built in hours.",
    "Pour the foundation today so tomorrow stands taller.",
    "One brick at a time. Start the clock.",
    "You are both the architect and the builder. Get to work.",
    "Blueprints mean nothing without execution. Let's build.",
    "Heavy work builds strong foundations.",
    "Every minute on the clock is concrete in your foundation.",
    "Don't just look at the blueprint — break ground.",
    // Climbing the ranks
    "Nobody reaches Skyscraper Legend by watching from the sidewalk.",
    "Put on your hard hat. The skyline is waiting.",
    "Another hour closer to the top floor.",
    "Trade excuses for effort. Raise the steel.",
    "Are you visiting the site today, or are you running it?",
    "The view is better from the top, and the only way up is one floor at a time.",
    "Outwork yesterday's blueprint.",
    "Every session raises your ceiling.",
    // Short & punchy
    "Break ground.",
    "Clock in. Build up.",
    "Lay the next brick.",
    "Under construction: a better you.",
    "Time is your raw material. Spend it well.",
    "Measure the effort. Build the result.",
    "Zero hours build zero floors. Start the timer.",
    // From earlier versions
    "Small hours, laid every day, become a skyline.",
    "You don't have to be fast. You have to keep showing up.",
    "Focus is a muscle. You're training it right now.",
    "Consistency beats intensity. Every single week.",
    "Today's hour is next month's skill.",
    "Measure twice, cut once — and then keep going.",
    "Every expert was once an apprentice with a timer.",
)

object ContextQuotes {
    const val MONDAY = "The scaffolding is clear and the week is fresh. Time to start the climb."
    const val STREAK = "Momentum is heavy — keep the crane moving."
    const val NIGHT = "Night shift on the site. These are the hours that set you apart."
    const val CLOSE = "Just a few more beams to lock in this week's goal. Finish strong."
}

/** What's going on right now, for picking a fitting line. */
data class QuoteContext(
    val day: DayOfWeek,
    val hour: Int,
    val streakDays: Int,
    /** True when the next rank or a weekly goal is within reach today. */
    val closeToGoal: Boolean,
)

/** Picks a line: a situation-specific one about half the time when one applies. */
fun pickQuote(ctx: QuoteContext, random: Random = Random.Default): String {
    val contextual = buildList {
        if (ctx.day == DayOfWeek.MONDAY) add(ContextQuotes.MONDAY)
        if (ctx.hour >= 22 || ctx.hour < 4) add(ContextQuotes.NIGHT)
        if (ctx.streakDays >= 3) add(ContextQuotes.STREAK)
        if (ctx.closeToGoal) add(ContextQuotes.CLOSE)
    }
    return if (contextual.isNotEmpty() && random.nextBoolean()) contextual.random(random) else Quotes.random(random)
}

/** The same line all day (for the home banner). */
fun quoteOfTheDay(date: LocalDate): String = Quotes[Math.floorMod(date.toEpochDay() * 31 + 7, Quotes.size.toLong()).toInt()]

/** "Good morning" / "Good afternoon" / "Good evening" / "Working late". */
fun greeting(hour: Int): String = when (hour) {
    in 4..11 -> "Good morning,"
    in 12..16 -> "Good afternoon,"
    in 17..21 -> "Good evening,"
    else -> "Working late,"
}
