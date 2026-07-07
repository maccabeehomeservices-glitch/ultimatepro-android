package com.ultimatepro.di

import android.content.Context
import com.ultimatepro.data.api.ApiService
import com.ultimatepro.data.api.NetworkClient
import com.ultimatepro.data.local.TokenStore
import com.ultimatepro.data.repository.CrmRepository
import com.ultimatepro.data.session.SessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideTokenStore(@ApplicationContext ctx: Context): TokenStore = TokenStore(ctx)

    @Provides @Singleton
    fun provideApiService(tokenStore: TokenStore): ApiService =
        NetworkClient.create(tokenStore)

    @Provides @Singleton
    fun provideRepository(api: ApiService, tokenStore: TokenStore, sessionManager: SessionManager): CrmRepository =
        CrmRepository(api, tokenStore, sessionManager)
}
