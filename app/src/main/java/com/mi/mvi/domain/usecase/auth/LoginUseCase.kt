package com.mi.mvi.domain.usecase.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.mi.mvi.domain.repository.AuthRepository
import com.mi.mvi.presentation.auth.state.AuthViewState
import com.mi.mvi.utils.DataState
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOn

@ExperimentalCoroutinesApi
class LoginUseCase(private val repository: AuthRepository) {

    fun invoke(email: String, password: String): LiveData<DataState<AuthViewState>> {
        return repository.login(email, password).flowOn(IO).asLiveData()
    }
}
