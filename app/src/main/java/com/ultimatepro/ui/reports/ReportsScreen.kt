package com.ultimatepro.ui.reports
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.compose.foundation.clickable; import androidx.compose.foundation.layout.*; import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape; import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons; import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*; import androidx.compose.runtime.*
import androidx.compose.ui.Alignment; import androidx.compose.ui.Modifier; import androidx.compose.ui.platform.LocalContext; import androidx.compose.ui.text.font.FontWeight; import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel; import androidx.lifecycle.viewModelScope
import com.ultimatepro.data.repository.*; import com.ultimatepro.domain.model.DashboardResponse; import com.ultimatepro.domain.model.SourceReport
import com.ultimatepro.ui.common.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*; import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.ui.unit.sp
@HiltViewModel
class ReportsViewModel @Inject constructor(private val repo:CrmRepository):ViewModel(){
    private val _r=MutableStateFlow<DashboardResponse?>(null); val report=_r.asStateFlow()
    private val _l=MutableStateFlow(true); val loading=_l.asStateFlow()
    private val _sources=MutableStateFlow<SourceReport?>(null); val sources=_sources.asStateFlow()
    private val _sourcesLoading=MutableStateFlow(false); val sourcesLoading=_sourcesLoading.asStateFlow()
    private val _exportMsg=MutableSharedFlow<String>(); val exportMsg=_exportMsg.asSharedFlow()
    init{load();loadSources()}
    fun load(){viewModelScope.launch{_l.value=true;when(val r=repo.getDashboardReport()){is Result.Success->_r.value=r.data;else->{}};_l.value=false}}
    fun loadSources(){viewModelScope.launch{_sourcesLoading.value=true;when(val r=repo.getSourceReport()){is Result.Success->_sources.value=r.data;else->{}};_sourcesLoading.value=false}}
    fun exportRevenue(context:Context){viewModelScope.launch{when(val r=repo.exportRevenueCsv()){is Result.Success->{saveCsv(context,r.data.bytes(),"revenue-report.csv");_exportMsg.emit("Revenue report saved to Downloads")};is Result.Error->_exportMsg.emit("Export failed: ${r.message}")}}}
    fun exportJobs(context:Context){viewModelScope.launch{when(val r=repo.exportJobsCsv()){is Result.Success->{saveCsv(context,r.data.bytes(),"jobs-report.csv");_exportMsg.emit("Jobs report saved to Downloads")};is Result.Error->_exportMsg.emit("Export failed: ${r.message}")}}}
    fun exportEarnings(context:Context){viewModelScope.launch{when(val r=repo.exportEarningsCsv()){is Result.Success->{saveCsv(context,r.data.bytes(),"earnings-report.csv");_exportMsg.emit("Earnings report saved to Downloads")};is Result.Error->_exportMsg.emit("Export failed: ${r.message}")}}}
    private fun saveCsv(context:Context,bytes:ByteArray,filename:String){val values=ContentValues().apply{put(MediaStore.Downloads.DISPLAY_NAME,filename);put(MediaStore.Downloads.MIME_TYPE,"text/csv");put(MediaStore.Downloads.IS_PENDING,1)};val uri=context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);uri?.let{context.contentResolver.openOutputStream(it)?.use{out->out.write(bytes)};values.clear();values.put(MediaStore.Downloads.IS_PENDING,0);context.contentResolver.update(it,values,null,null)}}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(onBack:()->Unit, onTimesheetReport:()->Unit={}, vm:ReportsViewModel= hiltViewModel()){
    val report by vm.report.collectAsState(); val loading by vm.loading.collectAsState()
    val sources by vm.sources.collectAsState(); val sourcesLoading by vm.sourcesLoading.collectAsState()
    val context=LocalContext.current
    val snackbarState=remember{SnackbarHostState()}
    LaunchedEffect(Unit){vm.exportMsg.collect{msg->snackbarState.showSnackbar(msg)}}
    Scaffold(
        snackbarHost={SnackbarHost(snackbarState)},
        topBar={Column{TopAppBar(title={Text("Reports",fontWeight=FontWeight.Bold)},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)}},actions={IconButton(onClick={vm.load();vm.loadSources()}){Icon(androidx.compose.ui.res.painterResource(com.ultimatepro.R.drawable.up_refresh),null)}});ShineHairline()}}
    ){padding->
        if(loading){LoadingView();return@Scaffold}
        val r=report
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)){
            // Timesheet Report shortcut
            Card(Modifier.fillMaxWidth().clickable{onTimesheetReport()},shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp)){
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){
                    Icon(Icons.Default.AccessTime,null,tint=AppColors.Blue,modifier=Modifier.size(28.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)){
                        Text("Timesheet Report",fontWeight=FontWeight.SemiBold)
                        Text("Clock-in/out hours by technician",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            r?.revenue?.let{rev->
                Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){
                    Column(Modifier.padding(16.dp)){
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                            Text("Revenue",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                            IconButton(onClick={vm.exportRevenue(context)},modifier=Modifier.size(32.dp)){Icon(Icons.Default.Download,"Export CSV",Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)}
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                            listOf("This Month" to formatMoney(rev.this_month),"Last Month" to formatMoney(rev.last_month),"Today" to formatMoney(rev.today)).forEach{(l,v)->
                                Column(horizontalAlignment=Alignment.CenterHorizontally){Text(v,fontWeight=FontWeight.Bold,color=AppColors.Blue);Text(l,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                            }
                        }
                    }
                }
            }
            r?.jobs?.let{jobs->
                Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){
                    Column(Modifier.padding(16.dp)){
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                            Text("Jobs",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                            IconButton(onClick={vm.exportJobs(context)},modifier=Modifier.size(32.dp)){Icon(Icons.Default.Download,"Export CSV",Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)}
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                            listOf("Total" to "${jobs.total}","Completed" to "${jobs.completed}","Cancelled" to "${jobs.cancelled}","Rate" to "${jobs.completion_rate_pct?.let{"%.0f".format(it)}?:"—"}%").forEach{(l,v)->
                                Column(horizontalAlignment=Alignment.CenterHorizontally){Text(v,fontWeight=FontWeight.Bold);Text(l,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                            }
                        }
                    }
                }
            }
            r?.calls?.let{calls->
                Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){
                    Column(Modifier.padding(16.dp)){
                        Text("Calls",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                            listOf("Total" to "${calls.total_calls}","Missed" to "${calls.missed}").forEach{(l,v)->
                                Column(horizontalAlignment=Alignment.CenterHorizontally){Text(v,fontWeight=FontWeight.Bold);Text(l,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                            }
                        }
                    }
                }
            }
            if(r?.top_techs?.isNotEmpty()==true){
                Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){
                    Column(Modifier.padding(16.dp)){
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                            Text("Top Technicians",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                            IconButton(onClick={vm.exportEarnings(context)},modifier=Modifier.size(32.dp)){Icon(Icons.Default.Download,"Export CSV",Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)}
                        }
                        Spacer(Modifier.height(8.dp))
                        r.top_techs.forEachIndexed{i,t->
                            Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
                                Text("${i+1}.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.width(20.dp))
                                val c=try{androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(t.color))} catch(e:Exception){AppColors.Blue}
                                AvatarCircle(t.initials,c,30.dp,10.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(t.fullName,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            // Sources ROI card
            val src = sources
            if (sourcesLoading) {
                Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){CircularProgressIndicator(Modifier.size(24.dp))}
            } else if (src != null && (src.network.isNotEmpty() || src.externalContacts.isNotEmpty() || src.ownCompany.isNotEmpty())) {
                Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){
                    Column(Modifier.padding(16.dp)){
                        Text("Job Sources",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        if(src.network.isNotEmpty()){
                            Text("Network Partners",style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.SemiBold,color=AppColors.Green)
                            src.network.forEach{row->SourceRow(row.sourceName,row.jobCount,row.totalRevenue,row.avgTicket)}
                            Spacer(Modifier.height(6.dp))
                        }
                        if(src.externalContacts.isNotEmpty()){
                            Text("Source Contacts",style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.SemiBold,color=AppColors.Blue)
                            src.externalContacts.forEach{row->SourceRow(row.sourceName,row.jobCount,row.totalRevenue,row.avgTicket,row.profitAllocationPct)}
                            Spacer(Modifier.height(6.dp))
                        }
                        if(src.ownCompany.isNotEmpty()){
                            Text("Ad Channels",style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.SemiBold,color=AppColors.Orange)
                            src.ownCompany.forEach{row->SourceRow(row.sourceName,row.jobCount,row.totalRevenue,row.avgTicket)}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(name:String,jobs:Int,revenue:Double,avg:Double,profitPct:Double?=null){
    Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f)){
            Text(name,fontWeight=FontWeight.Medium)
            profitPct?.let{if(it>0)Text("${it.toInt()}% allocation",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
        Column(horizontalAlignment=Alignment.End){
            Text(formatMoney(revenue),fontWeight=FontWeight.SemiBold,color=AppColors.Blue)
            Text("$jobs jobs · avg ${formatMoney(avg)}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
