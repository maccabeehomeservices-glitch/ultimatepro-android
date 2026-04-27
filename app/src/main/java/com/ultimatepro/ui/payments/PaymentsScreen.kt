package com.ultimatepro.ui.payments
import androidx.compose.foundation.layout.*; import androidx.compose.foundation.lazy.LazyColumn; import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons; import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*; import androidx.compose.runtime.*
import androidx.compose.ui.Alignment; import androidx.compose.ui.Modifier; import androidx.compose.ui.text.font.FontWeight; import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel; import androidx.lifecycle.viewModelScope
import com.ultimatepro.data.repository.*
import com.ultimatepro.domain.model.Payment; import com.ultimatepro.ui.common.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*; import kotlinx.coroutines.launch
import javax.inject.Inject
@HiltViewModel
class PaymentsViewModel @Inject constructor(private val repo:CrmRepository):ViewModel(){
    private val _p=MutableStateFlow<List<Payment>>(emptyList()); val payments=_p.asStateFlow()
    private val _l=MutableStateFlow(true); val loading=_l.asStateFlow()
    init{load()}
    @Suppress("UNCHECKED_CAST")
    fun load(){viewModelScope.launch{_l.value=true;when(val r=repo.getPayments()){is Result.Success->{val list=(r.data["payments"] as? List<*>)?.filterIsInstance<Map<String,Any>>()
        _p.value=list?.map{m->Payment(id=m["id"]?.toString()?:"",customer_id=m["customer_id"]?.toString()?:"",invoice_id=m["invoice_id"]?.toString(),amount=(m["amount"] as? Number)?.toDouble()?:0.0,method=m["method"]?.toString()?:"cash",status=m["status"]?.toString()?:"completed",notes=m["notes"]?.toString(),processed_at=m["processed_at"]?.toString(),invoice_number=m["invoice_number"]?.toString(),cust_first=m["cust_first"]?.toString(),cust_last=m["cust_last"]?.toString())}?:emptyList()};else->{}};_l.value=false}}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsScreen(onBack:()->Unit, vm:PaymentsViewModel= hiltViewModel()){
    val payments by vm.payments.collectAsState(); val loading by vm.loading.collectAsState()
    Scaffold(topBar={TopAppBar(title={Text("Payments",fontWeight=FontWeight.Bold)},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)}})}){padding->
        if(loading)LoadingView() else if(payments.isEmpty())EmptyView("No payments yet",Icons.Default.Payments) else
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            items(payments,key={it.id}){p->
                val sc=when(p.status){"completed"->AppColors.Green;"failed"->AppColors.Red;"refunded"->AppColors.Orange;else->AppColors.Slate}
                Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)){
                    Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                        Icon(when(p.method){"cash"->Icons.Default.Money;"check"->Icons.Default.Description;else->Icons.Default.CreditCard},null,tint=AppColors.Blue,modifier=Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)){
                            Text("${(p.cust_first?:"")} ${(p.cust_last?:"")}".trim().ifBlank{"Customer"},fontWeight=FontWeight.SemiBold)
                            Text(p.method.replaceFirstChar{it.uppercase()},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            p.processed_at?.take(10)?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        }
                        Column(horizontalAlignment=Alignment.End){Text(formatMoney(p.amount),fontWeight=FontWeight.Bold);Spacer(Modifier.height(4.dp));StatusBadge(p.status.replaceFirstChar{it.uppercase()},sc,small=true)}
                    }
                }
            }
        }
    }
}
