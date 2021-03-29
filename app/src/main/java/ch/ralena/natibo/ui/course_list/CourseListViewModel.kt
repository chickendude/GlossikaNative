package ch.ralena.natibo.ui.course_list

import ch.ralena.natibo.`object`.Course
import ch.ralena.natibo.ui.base.BaseViewModel
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmResults
import javax.inject.Inject

class CourseListViewModel @Inject constructor(
		private val realm: Realm
) : BaseViewModel<CourseListViewModel.Listener>() {
	interface Listener {

	}

	fun fetchCourses(): RealmResults<Course> =
			realm.where(Course::class.java).findAll()

}