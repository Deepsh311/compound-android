package app.compound.data

import java.time.LocalDate

data class Quote(val text: String, val author: String)

/** Public-domain and widely quoted lines, one a day. */
val QUOTES = listOf(
    Quote("Little strokes fell great oaks.", "Benjamin Franklin"),
    Quote("In the morning when thou risest unwillingly, let this thought be present\u2014I am rising to the work of a human being.", "Marcus Aurelius"),
    Quote("First say to yourself what you would be; and then do what you have to do.", "Epictetus"),
    Quote("Hold every hour in your grasp.", "Seneca"),
    Quote("We are what we repeatedly do. Excellence, then, is not an act, but a habit.", "Will Durant, on Aristotle"),
    Quote("Well done is better than well said.", "Benjamin Franklin"),
    Quote("The journey of a thousand miles begins with a single step.", "Lao Tzu"),
    Quote("Every habit and faculty is maintained and increased by the corresponding actions: the habit of walking by walking, the habit of running by running.", "Epictetus"),
    Quote("Strength does not come from physical capacity. It comes from an indomitable will.", "Mahatma Gandhi"),
    Quote("While we are postponing, life speeds by.", "Seneca"),
    Quote("Knowing is not enough; we must apply. Willing is not enough; we must do.", "Johann Wolfgang von Goethe"),
    Quote("Our life is frittered away by detail. Simplify, simplify.", "Henry David Thoreau"),
    Quote("Motivation is what gets you started. Habit is what keeps you going.", "Jim Ryun"),
    Quote("Lost time is never found again.", "Benjamin Franklin"),
    Quote("No longer talk at all about the kind of man that a good man ought to be, but be such.", "Marcus Aurelius"),
    Quote("Fall seven times, stand up eight.", "Japanese proverb"),
    Quote("It is not enough to be industrious; so are the ants. What are you industrious about?", "Henry David Thoreau"),
    Quote("Habit is a cable; we weave a thread of it each day, and at last we cannot break it.", "Horace Mann"),
    Quote("Do the thing, and you shall have the power.", "Ralph Waldo Emerson"),
    Quote("Begin at once to live, and count each separate day as a separate life.", "Seneca"),
    Quote("Our greatest glory is not in never falling, but in rising every time we fall.", "Oliver Goldsmith"),
    Quote("Discipline is the bridge between goals and accomplishment.", "Jim Rohn"),
    Quote("The man of virtue makes the difficulty to be overcome his first business, and success only a subsequent consideration.", "Confucius"),
    Quote("One today is worth two tomorrows.", "Benjamin Franklin"),
    Quote("Dripping water hollows out stone.", "Ovid"),
    Quote("The chains of habit are too weak to be felt until they are too strong to be broken.", "Samuel Johnson"),
    Quote("Little by little, one travels far.", "Spanish proverb"),
    Quote("Things do not change; we change.", "Henry David Thoreau"),
    Quote("Nothing will ever be attempted if all possible objections must be first overcome.", "Samuel Johnson"),
    Quote("The superior man is modest in his speech, but exceeds in his actions.", "Confucius"),
    Quote("Diligence is the mother of good luck.", "Benjamin Franklin"),
    Quote("Well begun is half done.", "Proverb"),
)

fun quoteFor(date: LocalDate, shift: Int = 0): Quote =
    QUOTES[Math.floorMod(date.toEpochDay() + shift, QUOTES.size.toLong()).toInt()]

fun cleanIdentity(text: String): String = text.trim()
    .replace(Regex("^i(\u2019|')?m\\s+someone\\s+who\\s+|^i\\s+am\\s+someone\\s+who\\s+", RegexOption.IGNORE_CASE), "")
    .trimEnd('.', ' ')
    .take(160)

fun starterData(identity: String, name: String, today: LocalDate): AppData {
    val t = today.toString()
    fun habit(name: String, seg: String, dur: Int, days: List<Int>, cue: String = "", two: String = "", bundle: String = "",
              prep: String = "", reward: String = "", gym: Boolean = false) =
        Habit(newId(), name, KIND_BUILD, seg, "", dur, days, cue, bundle, prep, two, reward, gym, t)

    val habits = listOf(
        habit("Make your bed", "morning", 0, ALL_DAYS, cue = "After I get up"),
        habit("Pick today\u2019s top three", "morning", 5, ALL_DAYS, cue = "After I make my bed"),
        habit("Read 10 pages", "morning", 15, ALL_DAYS, cue = "With my first drink of the day", two = "Read one page",
            bundle = "Only with my favourite coffee", prep = "Book on the pillow the night before"),
        habit("Deep work block", "afternoon", 90, listOf(1, 2, 3, 4, 5), two = "Open your notes and write one line",
            prep = "Phone in another room", reward = "A walk outside afterwards"),
        habit("Train", "evening", 75, listOf(1, 2, 4, 6), two = "Get changed and do one warm-up set",
            prep = "Gym bag packed by the door", reward = "Log it and watch the bar fill", gym = true),
        habit("Lay out tomorrow", "evening", 10, ALL_DAYS, cue = "After dinner"),
        Habit(newId(), "Scrolling in bed", KIND_BREAK, "evening", "", 0, ALL_DAYS,
            cue = "Phone charges in the kitchen", bundle = "Remember how groggy tomorrow feels",
            prep = "Log out of the apps that pull you in", reward = "Tell a friend; a slip costs you a coffee", created = t),
    )
    val goals = listOf(
        Goal(newId(), "Bench press 100 kg", why = "Be strong for life", habits = listOf(habits[4].id),
            becoming = "someone who trains, whatever the weather", stakes = "Strength fades without a reason to keep going",
            created = t),
        Goal(newId(), "Read 20 books this year", why = "Think more clearly and write better", habits = listOf(habits[2].id),
            becoming = "a reader", created = t),
    )
    fun ex(name: String, group: String, lo: Int, hi: Int, w: Double, inc: Double, bw: Boolean = false) =
        Exercise(newId(), name, group, 3, lo, hi, w, inc, bw, t)
    val exercises = listOf(
        ex("Bench press", "Push", 6, 10, 60.0, 2.5),
        ex("Overhead press", "Push", 8, 12, 30.0, 2.5),
        ex("Pull-up", "Pull", 6, 10, 0.0, 2.5, bw = true),
        ex("Barbell row", "Pull", 8, 12, 50.0, 2.5),
        ex("Squat", "Legs", 6, 10, 70.0, 5.0),
        ex("Romanian deadlift", "Legs", 8, 12, 60.0, 5.0),
    )
    val scorecard = listOf(
        ScoreItem(newId(), "Wake up", 0), ScoreItem(newId(), "Check phone in bed", -1),
        ScoreItem(newId(), "Make the bed", 1), ScoreItem(newId(), "Coffee", 0),
    )
    return AppData(onboarded = true, name = name.trim(), identity = cleanIdentity(identity), habits = habits, goals = goals,
        exercises = exercises, scorecard = scorecard, created = t)
}

fun emptyData(identity: String, name: String, today: LocalDate) =
    AppData(onboarded = true, name = name.trim(), identity = cleanIdentity(identity), created = today.toString())
