package com.ultimatepro.ui.calendar
import androidx.compose.foundation.*; import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn; import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape; import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons; import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*; import androidx.compose.runtime.*
import androidx.compose.ui.Alignment; import androidx.compose.ui.Modifier; import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color; import androidx.compose.ui.text.font.FontWeight; import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.ultimatepro.ui.common.*; import com.ultimatepro.ui.jobs.JobViewModel
import com.ultimatepro.util.formatJobInstant
import java.time.LocalDate; import java.time.format.DateTimeFormatter
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(onJob:(String)->Unit, onBack:()->Unit, vm:JobViewModel= hiltViewModel()) {
    val state by vm.state.collectAsState()
    var selected by remember{mutableStateOf(LocalDate.now())}
    LaunchedEffect(selected){vm.load(from=selected.toString(),to=selected.toString())}
    Scaffold(topBar={TopAppBar(title={Text("Calendar",fontWeight=FontWeight.Bold)},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)}},actions={IconButton(onClick={selected=LocalDate.now()}){Icon(Icons.Default.Today,null)}})}){padding->
        Column(Modifier.fillMaxSize().padding(padding)){
            // Week strip
            val wkStart=selected.minusDays(selected.dayOfWeek.value.toLong()-1)
            Row(Modifier.fillMaxWidth().padding(8.dp),horizontalArrangement=Arrangement.SpaceEvenly){
                (0..6).forEach{offset->
                    val day=wkStart.plusDays(offset.toLong())
                    val isSelected=day==selected; val isToday=day==LocalDate.now()
                    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable{selected=day}.background(if(isSelected)AppColors.Blue else Color.Transparent).padding(horizontal=10.dp,vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally){
                        Text(day.dayOfWeek.name.take(1),fontSize=10.sp,color=if(isSelected)Color.White else MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.size(30.dp).clip(CircleShape).background(if(isToday&&!isSelected)MaterialTheme.colorScheme.primaryContainer else Color.Transparent),contentAlignment=Alignment.Center){
                            Text(day.dayOfMonth.toString(),fontWeight=if(isSelected||isToday)FontWeight.Bold else FontWeight.Normal,color=when{isSelected->Color.White;isToday->AppColors.Blue;else->MaterialTheme.colorScheme.onSurface})
                        }
                    }
                }
            }
            HorizontalDivider()
            if(state.loading)LoadingView() else if(state.jobs.isEmpty())EmptyView("No jobs on ${selected.format(DateTimeFormatter.ofPattern("MMMM d"))}",Icons.Default.EventBusy)
            else LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                items(state.jobs,key={it.id}){job->
                    val sc=AppColors.jobStatus(job.status)
                    Card(onClick={onJob(job.id)},Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){
                        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                            Box(Modifier.width(4.dp).height(52.dp).clip(RoundedCornerShape(4.dp)).background(sc))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)){
                                job.scheduled_start?.let{Text(formatJobInstant(it, job.effective_timezone, "h:mm a zzz"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                                Text(job.title,fontWeight=FontWeight.SemiBold)
                                Text(job.customerName,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusBadge(job.status.replace("_"," ").replaceFirstChar{it.uppercase()},sc,small=true)
                        }
                    }
                }
            }
        }
    }
}
