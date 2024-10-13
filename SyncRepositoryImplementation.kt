package xcvi.dev.firebaseroom.data

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import xcvi.dev.firebaseroom.domain.Error
import xcvi.dev.firebaseroom.domain.Response
import xcvi.dev.firebaseroom.domain.RoutineModel
import xcvi.dev.firebaseroom.domain.SyncRepository

class SyncRepositoryImplementation(
    private val dao: RoutineDao,
) : SyncRepository {

    override suspend fun download(): Response<Unit, Error> {
        val routinesRef =
            FirebaseService.getRoutineRef() ?: return Response.Failure(Error.Authentication)
        val dataSnapshot = routinesRef.get().await()
        try {
            val items = dataSnapshot.children.mapNotNull { item ->
                item.getValue(RoutineModel::class.java)
            }
            if (items.isEmpty()) {
                return Response.Failure(Error.EmptyCloud)
            } else {
                dao.restoreRoutines(items)
                return Response.Success(Unit)
            }
        } catch (e: Exception) {
            return Response.Failure(Error.Connection(e.message.toString()))
        }
    }

    override suspend fun upload(): Response<Unit, Error> {
        val routinesRef =
            FirebaseService.getRoutineRef() ?: return Response.Failure(Error.Authentication)
        try {
            val routines = dao.getAllRoutines()
            if (routines.isEmpty()) {
                return Response.Failure(Error.EmptyLocalDB)
            } else {
                routinesRef.removeValue().await()
                routines.forEach {
                    routinesRef.child(it.routineId).setValue(it).await()
                }
            }
            return Response.Success(Unit)
        } catch (e: Exception) {
            return Response.Failure(Error.Connection(e.message.toString()))
        }
    }
}