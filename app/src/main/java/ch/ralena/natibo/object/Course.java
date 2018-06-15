package ch.ralena.natibo.object;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.Index;
import io.realm.annotations.PrimaryKey;


/*
 *
 * Sentence sets, each set will hold it's review schedule
 * The schedule will create the sentence sets, ie. will create a new sentence set each time you study
 * a new set of words.
 *
 * Creating a schedule:
 * OPTIONS:
 * 0.1 - select base/target language
 * 0.2 - select packs
 * 1. Pre-Packaged
 *	a. # sentences/day
 *	b. # days to review/reviews per day
 *		+ pre-set
 *		+ set manually
 * 2? Temporary schedule (eg. quick run-through of all sentences)
 *
 *
 * Course:
 * - realm list of sentence packs
 *
 */


public class Course extends RealmObject {
	@PrimaryKey
	@Index
	private String id = UUID.randomUUID().toString();

	private String title;
	private RealmList<Language> languages;
	private RealmList<Pack> packs;
	private Day currentDay;
	private int numReps;
	private int pauseMillis;
	private RealmList<Day> pastDays = new RealmList<>();
	private Schedule schedule = new Schedule();    // the different pieces that make up the study routine for each day
	private RealmList<Sentence> sentencesSeen = new RealmList<>();    // keep track of which sentences have been seen and which haven't

	// --- getters and setters ---

	public String getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public RealmList<Language> getLanguages() {
		return languages;
	}

	public void setLanguages(RealmList<Language> languages) {
		this.languages = languages;
	}

	public RealmList<Pack> getPacks() {
		return packs;
	}

	public void setPacks(RealmList<Pack> packs) {
		this.packs = packs;
	}

	public void setSchedule(Schedule schedule) {
		this.schedule = schedule;
	}

	public int getNumReps() {
		return numReps;
	}

	public void setNumReps(int numReps) {
		this.numReps = numReps;
	}

	public int getPauseMillis() {
		return pauseMillis;
	}

	public void setPauseMillis(int pauseMillis) {
		this.pauseMillis = pauseMillis;
		if (currentDay != null)
			currentDay.setPauseMillis(pauseMillis);
	}

	public RealmList<Day> getPastDays() {
		return pastDays;
	}

	public void setPastDays(RealmList<Day> pastDays) {
		this.pastDays = pastDays;
	}

	public Day getCurrentDay() {
		return currentDay;
	}

	public void setCurrentDay(Day currentDay) {
		this.currentDay = currentDay;
	}

	public Schedule getSchedule() {
		return schedule;
	}

	// --- helper methods ---

	public void setStartingSentenceForAllSchedules(Realm realm, Sentence sentence) {
		// remember, the sentence index starts at 1, not 0!
		realm.executeTransaction(r -> {
			schedule.setSentenceIndex(sentence.getIndex() - 1);
		});
	}

	public void addReps(int reps) {
		numReps += reps;
	}

	public void prepareNextDay(Realm realm) {
		// add current day to past days
		if (currentDay != null && currentDay.isCompleted()) {
			realm.executeTransaction(r -> pastDays.add(currentDay));
		}

		// create a new day
		realm.executeTransaction(r -> {
			Day day = r.createObject(Day.class, UUID.randomUUID().toString());

			// add the sentence sets from the current day to the next day
			if (currentDay != null) {
				day.getSentenceSets().addAll(currentDay.getSentenceSets());

				// move yesterday's new words to the front of the reviews
				SentenceSet lastSet = day.getSentenceSets().last();
				day.getSentenceSets().remove(lastSet);
				day.getSentenceSets().add(0, lastSet);
			}
			RealmList<Integer> reviewPattern = schedule.getReviewPattern();
			int numSentences = schedule.getNumSentences();
			int sentenceIndex = schedule.getSentenceIndex();
			schedule.setSentenceIndex(sentenceIndex + numSentences);

			// create new set of sentences based off the schedule
			SentenceSet sentenceSet = new SentenceSet();
			sentenceSet.setSentences(getSentenceGroups(sentenceIndex, numSentences));
			sentenceSet.setReviews(reviewPattern);
			sentenceSet.setFirstDay(true);
			sentenceSet.setOrder(schedule.getOrder());

			// add sentence set to list of sentencesets for the next day's studies
			day.getSentenceSets().add(sentenceSet);
			day.setCompleted(false);
			day.setPauseMillis(pauseMillis);
			currentDay = day;
		});
		List<SentenceSet> emptySentenceSets = new ArrayList<>();
		for (SentenceSet set : currentDay.getSentenceSets()) {
			// create sentence set and mark it to be deleted if it is empty
			if (!set.buildSentences(realm)) {
				emptySentenceSets.add(set);
			}
		}

		realm.executeTransaction(r -> currentDay.getSentenceSets().removeAll(emptySentenceSets));
	}

	private RealmList<SentenceGroup> getSentenceGroups(int index, int numSentences) {
		RealmList<SentenceGroup> sentenceGroups = new RealmList<>();

		// go through each pack
		for (Language language : languages) {
			int i = 0;
			for (Pack pack : getPacksPerLanguage(language)) {
				RealmList<Sentence> packSentences = pack.getSentences();
				if (index >= pack.getSentences().size())
					index -= packSentences.size();
				else {
					while (numSentences > 0) {
						if (index >= pack.getSentences().size())
							break;
						numSentences--;
						Sentence sentence = packSentences.get(index++);
						sentenceGroups.get(i).getSentences().add(sentence);
						sentenceGroups.get(i++).getLanguages().add(language);
					}
				}
			}
		}
		return sentenceGroups;
	}

	private RealmList<Pack> getPacksPerLanguage(Language language) {
		RealmList<Pack> langPacks = new RealmList<>();
		for (Pack pack : packs) {
			if (language.getPacks().contains(pack))
				langPacks.add(pack);
		}
		return langPacks;
	}

	public int getNumSentencesSeen() {
		int numSeen = 0;

		// count number of sentences we've studied in the past
		for (Day day : pastDays) {
			numSeen += day.getSentenceSets().get(0).getSentenceGroups().size();
		}

		// if the current day has been completed, add those as well
		if (currentDay != null && currentDay.isCompleted())
			numSeen += currentDay.getSentenceSets().get(0).getSentenceGroups().size();
		return numSeen;
	}

	public int getTotalReps() {
		int totalReps = numReps;
		if (currentDay != null && !currentDay.isCompleted())
			totalReps += currentDay.getTotalReviews() - currentDay.getNumReviewsLeft();
		return totalReps;
	}
}
