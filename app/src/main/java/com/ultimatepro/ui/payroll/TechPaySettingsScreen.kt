package com.ultimatepro.ui.payroll

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultimatepro.data.repository.*
import com.ultimatepro.ui.common.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.vector.ImageVector

data class TechProfileState(
    val loading:Boolean=true, val saving:Boolean=false, val sending:Boolean=false,
    val error:String?=null, val message:String?=null,
    val firstName:String="", val lastName:String="",
    val email:String="", val phone:String="", val phone2:String="",
    val address:String="", val city:String="", val stateCode:String="", val zip:String="",
    val emergencyName:String="", val emergencyPhone:String="",
    val workerType:String="employee", val materialPolicy:String="company_supplied",
    val commissionPct:String="0", val hourlyRate:String="0", val subPct:String="0",
    val preferredPay:String="check", val reimbNote:String="", val internalNotes:String=""
)

@HiltViewModel
class TechSettingsViewModel @Inject constructor(private val repo:CrmRepository):ViewModel(){
    private val _s=MutableStateFlow(TechProfileState()); val state=_s.asStateFlow()

    fun load(userId:String){viewModelScope.launch{_s.update{it.copy(loading=true)}
        when(val r=repo.getUser(userId)){
            is Result.Success->{val u=r.data; _s.update{it.copy(loading=false,firstName=u.first_name,lastName=u.last_name,email=u.email,phone=u.phone?:"",phone2=u.phone2?:"",address=u.address?:"",city=u.city?:"",stateCode=u.state?:"",zip=u.zip?:"",emergencyName=u.emergency_name?:"",emergencyPhone=u.emergency_phone?:"",workerType=u.worker_type?:"employee",materialPolicy=u.material_policy?:"company_supplied",commissionPct=u.commission_pct.toString(),hourlyRate=u.hourly_rate.toString(),subPct=u.sub_pct.toString(),preferredPay=u.preferred_pay_method?:"check")}}
            is Result.Error->_s.update{it.copy(loading=false,error=r.message)}
        }
    }}

    fun save(userId:String,onDone:()->Unit){viewModelScope.launch{_s.update{it.copy(saving=true)}
        val s=_s.value
        val data = mapOf("first_name" to s.firstName,"last_name" to s.lastName,"email" to s.email,"phone" to s.phone.ifBlank{null},"phone2" to s.phone2.ifBlank{null},"address" to s.address.ifBlank{null},"city" to s.city.ifBlank{null},"state" to s.stateCode.ifBlank{null},"zip" to s.zip.ifBlank{null},"emergency_name" to s.emergencyName.ifBlank{null},"emergency_phone" to s.emergencyPhone.ifBlank{null},"worker_type" to s.workerType,"material_policy" to s.materialPolicy,"commission_pct" to s.commissionPct.toDoubleOrNull(),"hourly_rate" to s.hourlyRate.toDoubleOrNull(),"sub_pct" to s.subPct.toDoubleOrNull(),"preferred_pay_method" to s.preferredPay,"reimbursement_note" to s.reimbNote.ifBlank{null},"internal_notes" to s.internalNotes.ifBlank{null})
        when(repo.saveUserProfile(userId,data)){is Result.Success->{_s.update{it.copy(saving=false,message="Profile saved")};onDone()};is Result.Error->_s.update{it.copy(saving=false,error="Failed to save")}}
    }}

    // P2.5: payroll send-report removed — it POSTed to the phantom
    // payroll/send-report/{userId} (no such backend route). Rebuild against the
    // real POST /reports/tech/:userId/send (needs from/to, not period) if wanted.

    fun update(block:TechProfileState.()->TechProfileState){_s.update(block)}
    fun clearMessages(){_s.update{it.copy(error=null,message=null)}}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechPaySettingsScreen(userId:String, onBack:()->Unit, vm:TechSettingsViewModel= hiltViewModel()){
    val state by vm.state.collectAsState()
    val snack=remember{SnackbarHostState()}
    var tab by remember{mutableIntStateOf(0)}
    LaunchedEffect(userId){vm.load(userId)}
    LaunchedEffect(state.message){if(state.message!=null){snack.showSnackbar(state.message!!);vm.clearMessages()}}
    LaunchedEffect(state.error){if(state.error!=null){snack.showSnackbar(state.error!!);vm.clearMessages()}}

    Scaffold(snackbarHost={SnackbarHost(snack)},topBar={Column{TopAppBar(
        title={Column{Text(if(state.firstName.isNotBlank())"${state.firstName} ${state.lastName}".trim() else "Tech Profile",fontWeight=FontWeight.Bold);Text("Profile & Pay Settings",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}},
        navigationIcon={IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)}},
        actions={
            TextButton(onClick={vm.save(userId,onBack)},enabled=!state.saving){
                if(state.saving)CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp)
                else Text("Save",fontWeight=FontWeight.Bold)
            }
        }
    );ShineHairline()}}){padding->
        if(state.loading){LoadingView();return@Scaffold}
        Column(Modifier.fillMaxSize().padding(padding)){
            TabRow(selectedTabIndex=tab){listOf("Contact","Pay Rate","Materials").forEachIndexed{i,l->Tab(selected=tab==i,onClick={tab=i},text={Text(l)})}}
            when(tab){0->ContactTab(state,vm);1->PayRateTab(state,vm);2->MaterialsTab(state,vm)}
        }
    }
}

@Composable
private fun ContactTab(s:TechProfileState,vm:TechSettingsViewModel){
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
        SectionLabel("FULL NAME")
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
            OutlinedTextField(s.firstName,{vm.update{copy(firstName=it)}},label={Text("First *")},singleLine=true,modifier=Modifier.weight(1f),shape=RoundedCornerShape(12.dp))
            OutlinedTextField(s.lastName,{vm.update{copy(lastName=it)}},label={Text("Last")},singleLine=true,modifier=Modifier.weight(1f),shape=RoundedCornerShape(12.dp))
        }
        SectionLabel("CONTACT")
        OutlinedTextField(s.email,{vm.update{copy(email=it)}},label={Text("Email *")},leadingIcon={Icon(Icons.Default.Email,null)},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email))
        OutlinedTextField(s.phone,{vm.update{copy(phone=it)}},label={Text("Phone (Primary)")},leadingIcon={Icon(Icons.Default.Phone,null)},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone))
        OutlinedTextField(s.phone2,{vm.update{copy(phone2=it)}},label={Text("Phone (Secondary)")},leadingIcon={Icon(Icons.Default.Phone,null)},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone))
        SectionLabel("HOME ADDRESS")
        Text("Used on payroll reports and 1099 forms.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(s.address,{vm.update{copy(address=it)}},label={Text("Street Address")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(s.city,{vm.update{copy(city=it)}},label={Text("City")},singleLine=true,modifier=Modifier.weight(2f),shape=RoundedCornerShape(12.dp))
            OutlinedTextField(s.stateCode,{vm.update{copy(stateCode=it)}},label={Text("ST")},singleLine=true,modifier=Modifier.weight(1f),shape=RoundedCornerShape(12.dp))
            OutlinedTextField(s.zip,{vm.update{copy(zip=it)}},label={Text("ZIP")},singleLine=true,modifier=Modifier.weight(1f),shape=RoundedCornerShape(12.dp),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
        }
        SectionLabel("EMERGENCY CONTACT")
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
            OutlinedTextField(s.emergencyName,{vm.update{copy(emergencyName=it)}},label={Text("Name")},singleLine=true,modifier=Modifier.weight(1f),shape=RoundedCornerShape(12.dp))
            OutlinedTextField(s.emergencyPhone,{vm.update{copy(emergencyPhone=it)}},label={Text("Phone")},singleLine=true,modifier=Modifier.weight(1f),shape=RoundedCornerShape(12.dp),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone))
        }
        SectionLabel("INTERNAL NOTES (Owner only)")
        OutlinedTextField(s.internalNotes,{vm.update{copy(internalNotes=it)}},label={Text("Notes")},minLines=3,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),placeholder={Text("Performance notes, agreements, equipment assigned...")})
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun PayRateTab(s:TechProfileState,vm:TechSettingsViewModel){
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)){
        SectionLabel("WORKER TYPE")
        listOf("employee" to "Employee (W-2)","subcontractor" to "Subcontractor (1099)").forEach{(v,l)->
            Card(onClick={vm.update{copy(workerType=v)}},Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),
                colors=CardDefaults.cardColors(containerColor=if(s.workerType==v)AppColors.Blue.copy(.1f) else MaterialTheme.colorScheme.surface),
                border=if(s.workerType==v)androidx.compose.foundation.BorderStroke(2.dp,AppColors.Blue) else null){
                Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
                    RadioButton(selected=s.workerType==v,onClick={vm.update{copy(workerType=v)}})
                    Spacer(Modifier.width(8.dp));Text(l,fontWeight=if(s.workerType==v)FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        if(s.workerType=="employee"){
            SectionLabel("PAY RATE  (set one, leave other as 0)")
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                OutlinedTextField(s.hourlyRate,{vm.update{copy(hourlyRate=it)}},label={Text("Hourly (\$)")},singleLine=true,modifier=Modifier.weight(1f),shape=RoundedCornerShape(12.dp),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                OutlinedTextField(s.commissionPct,{vm.update{copy(commissionPct=it)}},label={Text("Commission (%)")},singleLine=true,modifier=Modifier.weight(1f),shape=RoundedCornerShape(12.dp),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
            }
            val rH=s.hourlyRate.toDoubleOrNull()?:0.0;val rC=s.commissionPct.toDoubleOrNull()?:0.0
            val desc=when{rH>0&&rC==0.0->"✓ Hourly: earns \$${"%.2f".format(rH)}/hr";rC>0&&rH==0.0->"✓ Commission: earns ${rC.toInt()}% of net profit";rH>0&&rC>0->"⚠️ Both set — commission takes priority";else->"No rate set — tech earns \$0"}
            Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(10.dp),colors=CardDefaults.cardColors(containerColor=AppColors.Blue.copy(.07f))){Text(desc,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(12.dp))}
        } else {
            SectionLabel("SUBCONTRACTOR RATE")
            OutlinedTextField(s.subPct,{vm.update{copy(subPct=it)}},label={Text("% of Gross Job Total")},leadingIcon={Icon(Icons.Default.Percent,null)},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
            val p=s.subPct.toDoubleOrNull()?:0.0
            Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(10.dp),colors=CardDefaults.cardColors(containerColor=AppColors.Blue.copy(.07f))){Text("On a \$1,000 job: sub gets ${formatMoney(1000.0*p/100)}, company keeps ${formatMoney(1000.0*(1-p/100))}",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(12.dp))}
        }
        SectionLabel("PREFERRED PAYMENT METHOD")
        LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            items(listOf("check" to "Check","cash" to "Cash","zelle" to "Zelle","venmo" to "Venmo","direct_deposit" to "Direct Deposit","other" to "Other")){(v,l)->FilterChip(selected=s.preferredPay==v,onClick={vm.update{copy(preferredPay=v)}},label={Text(l)})}
        }
        SectionLabel("PAY ARRANGEMENT NOTES")
        OutlinedTextField(s.reimbNote,{vm.update{copy(reimbNote=it)}},label={Text("Notes")},minLines=2,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),placeholder={Text("e.g. Reimbursed via Zelle same day as job")})
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun MaterialsTab(s:TechProfileState,vm:TechSettingsViewModel){
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
        if(s.workerType=="subcontractor"){
            Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),colors=CardDefaults.cardColors(containerColor=AppColors.Slate.copy(.1f))){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Info,null,tint=AppColors.Slate);Spacer(Modifier.width(10.dp));Text("Subcontractors receive a flat % of gross. Material policy does not apply.",style=MaterialTheme.typography.bodySmall)}}
            return@Column
        }
        SectionLabel("PARTS & MATERIALS POLICY")
        Text("How are parts handled when this tech is on a job?",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        MaterialPolicyOption("company_supplied",s.materialPolicy,"Company supplies all parts","You order the parts. Material cost is deducted before commission.",Icons.Default.Warehouse,"\$1,000 job − \$150 parts = \$850 → tech 40% = \$340"){vm.update{copy(materialPolicy="company_supplied")}}
        MaterialPolicyOption("tech_reimbursed",s.materialPolicy,"Tech buys parts — you reimburse","Tech pays out of pocket, gets commission on full job + separate reimbursement.",Icons.Default.Receipt,"\$1,000 job / tech paid \$150 → commission on \$1,000 + \$150 back"){vm.update{copy(materialPolicy="tech_reimbursed")}}
        MaterialPolicyOption("tech_keeps_markup",s.materialPolicy,"Tech supplies parts — keeps markup","Tech buys at cost, charges markup. Keeps markup. Commission on labor only.",Icons.Default.TrendingUp,"\$1,000 / part cost \$80 charged \$150 → keeps \$70 + commission on \$850"){vm.update{copy(materialPolicy="tech_keeps_markup")}}
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun MaterialPolicyOption(value:String,selected:String,title:String,subtitle:String,icon:ImageVector,example:String,onClick:()->Unit){
    val isSelected=value==selected
    Card(onClick=onClick,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),
        colors=CardDefaults.cardColors(containerColor=if(isSelected)AppColors.Blue.copy(.1f) else MaterialTheme.colorScheme.surface),
        border=if(isSelected)androidx.compose.foundation.BorderStroke(2.dp,AppColors.Blue) else null){
        Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
            RadioButton(selected=isSelected,onClick=onClick)
            Spacer(Modifier.width(8.dp))
            Icon(icon,null,tint=if(isSelected)AppColors.Blue else MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)){
                Text(title,fontWeight=if(isSelected)FontWeight.SemiBold else FontWeight.Normal)
                Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(example,style=MaterialTheme.typography.labelSmall,color=if(isSelected)AppColors.Blue else MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=2.dp))
            }
        }
    }
}

// P2.5: SendReportDialog removed with the phantom payroll/send-report path.
