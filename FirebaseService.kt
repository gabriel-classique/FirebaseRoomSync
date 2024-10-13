package xcvi.dev.firebaseroom.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import xcvi.dev.firebaseroom.domain.Error
import xcvi.dev.firebaseroom.domain.Response
import xcvi.dev.firebaseroom.domain.RoutineModel
import xcvi.dev.firebaseroom.domain.UserModel


object FirebaseService {

    private const val DATABASE_URL =
        "https://procreator-cloud-service-default-rtdb.europe-west1.firebasedatabase.app"
    private const val USER_PATH = "User"
    private const val STEPS_PATH = "Data"
    private const val FOOD_PATH = "Food"
    private const val WEIGHT_PATH = "Weight"
    private const val PROTOCOL_PATH = "Protocol"
    private const val TRAINING_PATH = "Training"


    private val db = FirebaseDatabase.getInstance(DATABASE_URL)
    private val auth = Firebase.auth


    init {
        db.setPersistenceEnabled(true)
    }

    fun getRoutineRef(): DatabaseReference? {
        val user = auth.currentUser ?: return null
        return getDatabaseReference().child(TRAINING_PATH).child(user.uid).child("RoutineModel")
    }
    fun getSetsRef(exerciseId: String): DatabaseReference? {
        val user = auth.currentUser ?: return null
        return getDatabaseReference().child(TRAINING_PATH).child(user.uid).child("ExerciseModel").child(exerciseId).child("SetModel")
    }
    fun getExerciseRef(): DatabaseReference? {
        val user = auth.currentUser ?: return null
        return getDatabaseReference().child(TRAINING_PATH).child(user.uid).child("ExerciseModel")
    }

    fun getDatabaseReference(): DatabaseReference {
        return db.reference
    }

    fun getAuth(): FirebaseAuth {
        return auth
    }

    suspend fun getUser(): Response<UserModel, Error> {
        val uid = getUid() ?: return Response.Failure(Error.Authentication)
        if (isUserVerified()) {
            val query = db.reference.child(USER_PATH).child(uid)
            val dataSnapshot = query.get().await()
            try {
                val user = dataSnapshot.getValue(UserModel::class.java) ?: return Response.Failure(
                    Error.Authentication
                )
                val athlete = UserModel(
                    uid = user.uid,
                    name = user.name,
                    age = user.age,
                    email = user.email,
                    height = user.height,
                    sex = user.sex,
                    measurement = user.measurement
                )
                return Response.Success(athlete)
            } catch (e: Exception) {
                return Response.Failure(Error.Authentication)
            }
        } else {
            return Response.Failure(Error.Authentication)
        }
    }


    fun login(
        email: String,
        password: String,
        onResult: (Response<UserModel, Error>) -> Unit,
    ) {
        try {
            if (email.isNotBlank() && password.isNotBlank()) {
                auth.signInWithEmailAndPassword(email, password).addOnSuccessListener { result ->
                    if (result.user == null) {
                        onResult(Response.Failure(Error.Authentication))
                        return@addOnSuccessListener
                    } else {
                        if (!result.user!!.isEmailVerified) {
                            onResult(Response.Failure(Error.Authentication))
                            return@addOnSuccessListener
                        }
                        val query = db.reference.child(USER_PATH).child(result.user!!.uid)
                        query.get().addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val user = task.result.getValue(UserModel::class.java)
                                if (user == null) {
                                    onResult(Response.Failure(Error.Authentication))
                                } else {
                                    onResult(Response.Success(user))
                                }
                            } else {
                                onResult(Response.Failure(Error.Authentication))
                            }
                        }
                    }
                }.addOnFailureListener {
                    onResult(Response.Failure(Error.Authentication))
                }

            } else {
                onResult(Response.Failure(Error.InputError))
            }
        } catch (e: Exception) {
            onResult(Response.Failure(Error.Connection(e.message.toString())))
        }
    }

    fun register(
        email: String,
        password: String,
        onResult: (Response<Unit, Error>) -> Unit,
    ) {
        try {
            if (email.isNotBlank() && password.isNotBlank()) {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener lambda@{ result ->
                        val user = result.user!!
                        db.reference.child(USER_PATH).child(user.uid).setValue(
                            UserModel(
                                uid = result.user!!.uid,
                                email = email,
                            )
                        ).addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                user.sendEmailVerification().addOnCompleteListener {
                                    onResult(Response.Success(Unit))
                                }.addOnFailureListener {
                                    onResult(Response.Failure(Error.Connection(it.message.toString())))
                                }
                            } else {
                                onResult(Response.Failure(Error.Connection(task.exception?.message.toString())))
                            }
                        }.addOnFailureListener {
                            println(it.message.toString())
                            onResult(Response.Failure(Error.Connection(it.message.toString())))
                        }
                    }.addOnFailureListener {
                        onResult(Response.Failure(Error.Connection(it.message.toString())))
                    }
            } else {
                onResult(Response.Failure(Error.Authentication))
            }
        } catch (e: Exception) {
            onResult(Response.Failure(Error.Connection(e.message.toString())))
        }
    }

    fun logout() {
        auth.signOut()
    }

    private fun isUserVerified(): Boolean {
        return auth.currentUser?.isEmailVerified ?: false
    }

    private fun getUid(): String? {
        return auth.currentUser?.uid
    }

    fun recoverAccount(email: String, onResult: (Response<Unit, Error>) -> Unit) {
        auth.sendPasswordResetEmail(email).addOnCompleteListener {
            if (it.isSuccessful) {
                onResult(Response.Success(Unit))
            } else {
                onResult(Response.Failure(Error.Unknown))
            }
        }.addOnFailureListener {
            onResult(Response.Failure(Error.Connection(it.message.toString())))
        }
    }

}

/*
   fun observeData(): Flow<List<DataEntity>> {
       return callbackFlow {
           auth.currentUser?.let { user ->
               val postListener = object : ValueEventListener {
                   override fun onCancelled(error: DatabaseError) {
                       this@callbackFlow.trySendBlocking(emptyList())
                   }

                   override fun onDataChange(dataSnapshot: DataSnapshot) {
                       val items = dataSnapshot.children.map { item ->
                           item.getValue(DataEntity::class.java)
                       }.mapNotNull {
                           it?.let {
                               DataEntity(it.id, it.uid, it.content)
                           }
                       }
                       this@callbackFlow.trySendBlocking(items)
                   }
               }
               db.reference.child(DATA_PATH).child(user.uid).addValueEventListener(postListener)

               awaitClose {
                   db.reference.child(DATA_PATH).child(user.uid)
                       .addValueEventListener(postListener)
               }
           }
       }
   }

   suspend fun getData(id: String): Response<DataEntity, Error> {
       val uid = auth.currentUser?.uid
       if (uid != null) {
           val query = db.reference.child(DATA_PATH).child(uid).child(id)
           val dataSnapshot = query.get().await()
           try {
               val item = dataSnapshot.getValue(DataEntity::class.java) ?: return Response.Failure(
                   Error.NotFound
               )
               return Response.Success(item)
           } catch (e: Exception) {
               return Response.Failure(Error.Connection(e.message.toString()))
           }
       } else {
           return Response.Failure(Error.Authentication)
       }
   }

   suspend fun getAll(): Response<List<DataEntity>, Error> {
       val uid = auth.currentUser?.uid
       return if (uid != null) {
           val query = db.reference.child(DATA_PATH).child(uid)
           val dataSnapshot = query.get().await()
           try {
               val items = dataSnapshot.children.mapNotNull { item ->
                   item.getValue(DataEntity::class.java)
               }
               return Response.Success(items)
           } catch (e: Exception) {
               Response.Failure(Error.Connection(e.message.toString()))
           }

       } else {
           Response.Failure(Error.Authentication)
       }

   }

   fun saveData(): Response<Task<Void>, Error> {
       val user = auth.currentUser
       if (user != null) {
           val dataEntity = DataEntity(
               id = "${LocalDateTime.now().hour}:${LocalDateTime.now().minute}:${LocalDateTime.now().second}",
               uid = user.uid,
               content = "Content of ${LocalDateTime.now().hour}:${LocalDateTime.now().minute}:${LocalDateTime.now().second}"
           )
           return Response.Success(
               db.reference
                   .child(DATA_PATH)
                   .child(user.uid)
                   .child(dataEntity.id)
                   .setValue(dataEntity)
           )

       } else {
           return Response.Failure(Error.Authentication)
       }
   }


   fun saveData(dataEntity: DataEntity, onSuccess: () -> Unit, onFailure: (Error) -> Unit = {}) {
       val user = auth.currentUser
       if (user != null) {
           db.reference
               .child(DATA_PATH)
               .child(user.uid)
               .child(dataEntity.id)
               .setValue(dataEntity)
               .addOnCompleteListener {
                   onSuccess()
               }
               .addOnFailureListener {
                   onFailure(Error.Connection(it.message.toString()))
               }
       } else {
           onFailure(Error.Authentication)
       }
   }

   fun deleteData(id: String, onSuccess: () -> Unit) {
       auth.currentUser?.let { user ->
           db.reference
               .child(DATA_PATH)
               .child(user.uid)
               .child(id)
               .removeValue().addOnCompleteListener {
                   onSuccess()
               }
       }
   }

    */
