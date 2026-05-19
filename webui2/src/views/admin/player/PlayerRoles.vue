<script setup lang="ts">
import {onMounted, ref, reactive} from "vue";
import {Message, Modal} from "@arco-design/web-vue";
import Request from "../../../api/Request";
import ItemPicker from "../../../components/ItemPicker.vue";

const loading = ref(false);

const searchForm = reactive({
	pageNum: 1,
	pageSize: 10,
	account: "",
	name: "",
	job: "",
	minLev: undefined as number | undefined,
	maxLev: undefined as number | undefined
});

const pageResult = ref({
	page: 1,
	totalPageSize: 10,
	totalSize: 0,
	list: [] as any[]
});

const editOption = ref({
	open: false,
	form: {} as Record<string, any>
});

const sendMailOption = ref({
	open: false,
	receiveCharacNo: 0,
	receiveCharacName: ""
});

const mailForm = ref({
	sendCharacNo: 0,
	message: "",
	sendCharacName: "DNF Manageer",
	receiveCharacNo: 0,
	item: null as any,
	itemId: undefined as number | undefined,
	addInfo: 1,
	upgrade: 0,
	seperateUpgrade: 0,
	sealFlag: false,
	amplifyOption: undefined as number | undefined,
	amplifyValue: 0,
	gold: 0
});

const windowHeight = ref(window.innerHeight - 250);

const jobs = ref([
	{
		name: "鬼剑士(男)",
		subs: {
			"0": "未转职",
			"1": "剑魂", "2": "鬼泣", "3": "狂战士", "4": "阿修罗",
			"17": "剑圣", "18": "弑魂", "19": "狱血魔神", "20": "大暗黑天"
		}
	},
	{
		name: "格斗家(女)",
		subs: {
			"0": "未转职",
			"1": "气功师", "2": "散打", "3": "街霸", "4": "柔道家",
			"17": "百花缭乱", "18": "武神", "19": "毒王", "20": "暴风眼"
		}
	},
	{
		name: "神枪手(男)",
		subs: {
			"0": "未转职",
			"1": "漫游枪手", "2": "枪炮师", "3": "机械师", "4": "弹药专家",
			"17": "枪神", "18": "狂暴者", "19": "机械战神", "20": "大将军"
		}
	},
	{
		name: "魔法师(女)",
		subs: {
			"0": "未转职",
			"1": "元素师", "2": "召唤师", "3": "战斗法师", "4": "魔道学者",
			"17": "大魔导师", "18": "月之女皇", "19": "贝亚娜斗神", "20": "魔术师"
		}
	},
	{
		name: "圣职者(男)",
		subs: {
			"0": "未转职",
			"1": "圣骑士", "2": "蓝拳圣使", "3": "驱魔师", "4": "复仇者",
			"17": "天启者", "18": "神之手", "19": "龙斗士", "20": "末日守护者"
		}
	},
	{
		name: "神枪手(女)",
		subs: {
			"0": "未转职",
			"1": "漫游枪手", "2": "枪炮师", "3": "机械师", "4": "弹药专家",
			"17": "沾血蔷薇", "18": "重炮掌控者", "19": "机械之心", "20": "战争女神"
		}
	},
	{
		name: "暗夜使者(女)",
		subs: {
			"0": "未转职",
			"1": "刺客", "2": "死灵术士",
			"17": "银月", "18": "灵魂收割者"
		}
	},
	{
		name: "格斗家(男)",
		subs: {
			"0": "未转职",
			"1": "气功师", "2": "散打", "3": "街霸", "4": "柔道家",
			"17": "狂虎帝", "18": "武极", "19": "千手罗汉", "20": "风林火山"
		}
	},
	{
		name: "魔法师(男)",
		subs: {
			"0": "未转职",
			"1": "元素师", "2": "冰结者",
			"17": "元素爆破师", "18": "冰冻之心"
		}
	},
	{
		name: "黑暗武士(男)",
		subs: {
			"0": "未转职",
			"1": "黑暗武士",
			"17": "自我觉醒"
		}
	}
]);

const getJobName = (job: number | string | undefined) => {
	const index = Number(job);
	return jobs.value[index]?.name ?? `未知职业(${job ?? "-"})`;
};

const getGrowTypeName = (job: number | string | undefined, growType: number | string | undefined) => {
	const jobIndex = Number(job);
	const key = String(growType ?? "");
	const subs = jobs.value[jobIndex]?.subs as Record<string, string> | undefined;
	return subs?.[key] ?? `_:${growType ?? "-"}`;
};

const search = (resetPage = false) => {
	if (resetPage) {
		searchForm.pageNum = 1;
	}

	let url = `api/v1/charac?page=${searchForm.pageNum}&pageSize=${searchForm.pageSize}`;
	url += searchForm.name ? `&name=${searchForm.name}` : "";
	url += searchForm.account ? `&account=${searchForm.account}` : "";
	url += searchForm.job !== "" && searchForm.job !== undefined ? `&job=${searchForm.job}` : "";
	url += searchForm.minLev ? `&minLev=${searchForm.minLev}` : "";
	url += searchForm.maxLev ? `&maxLevel=${searchForm.maxLev}` : "";

	loading.value = true;
	Request.get(url).then((response) => {
		pageResult.value = response.data;
		if (pageResult.value && typeof (pageResult.value as any).page === "number") {
			searchForm.pageNum = (pageResult.value as any).page;
		}
	}).catch((e: any) => {
		Message.error(e?.message || "查询失败");
	}).finally(() => {
		loading.value = false;
	});
};

const onPageChange = (page: number) => {
	searchForm.pageNum = page;
	search(false);
};

const onPageSizeChange = (pageSize: number) => {
	searchForm.pageSize = pageSize;
	search(true);
};

const openEdit = (record: any) => {
	const form = JSON.parse(JSON.stringify(record || {}));
	form.growType = getGrowTypeName(form.job, form.growType);
	form.job = getJobName(form.job);
	editOption.value.form = form;
	editOption.value.open = true;
};

const submitEdit = async () => {
	if (!editOption.value.form?.characNo) return;
	const payload = {...editOption.value.form};
	delete payload.job;
	delete payload.growType;
	try {
		await Request.put("api/v1/charac", payload);
		Message.success("修改成功，请上游戏查看");
		editOption.value.open = false;
		search(false);
	} catch (e: any) {
		Message.error(e?.message || "修改失败");
	}
};

const openSendMail = (record: any) => {
	sendMailOption.value.receiveCharacNo = record?.characNo ?? 0;
	sendMailOption.value.receiveCharacName = record?.characName ?? "";
	mailForm.value = {
		sendCharacNo: 0,
		message: "",
		sendCharacName: "DNF Manageer",
		receiveCharacNo: record?.characNo ?? 0,
		item: null,
		itemId: undefined,
		addInfo: 1,
		upgrade: 0,
		seperateUpgrade: 0,
		sealFlag: false,
		amplifyOption: undefined,
		amplifyValue: 0,
		gold: 0
	};
	sendMailOption.value.open = true;
};

const submitSendMail = async () => {
	mailForm.value.receiveCharacNo = sendMailOption.value.receiveCharacNo;
	mailForm.value.itemId = mailForm.value.item?.id ?? null;
	if (!mailForm.value.itemId) {
		Message.error("必须选择一个物品");
		return;
	}
	const payload = {
		sendCharacNo: mailForm.value.sendCharacNo,
		message: mailForm.value.message,
		sendCharacName: mailForm.value.sendCharacName,
		receiveCharacNo: mailForm.value.receiveCharacNo,
		itemId: mailForm.value.itemId,
		addInfo: mailForm.value.addInfo,
		upgrade: mailForm.value.upgrade,
		seperateUpgrade: mailForm.value.seperateUpgrade,
		sealFlag: mailForm.value.sealFlag,
		amplifyOption: mailForm.value.amplifyOption ?? 0,
		amplifyValue: mailForm.value.amplifyValue,
		gold: mailForm.value.gold
	};
	try {
		await Request.post("api/v1/postal", payload);
		Message.success("发送成功, 重新选择角色即可查看邮件");
		sendMailOption.value.open = false;
	} catch (e: any) {
		Message.error(e?.message || "发送失败");
	}
};

const overTasks = (characNo: number) => {
	Modal.confirm({
		title: "提示",
		content: "确定要完成该角色的所有任务吗？",
		okText: "确定",
		cancelText: "取消",
		onOk: async () => {
			try {
				await Request.post(`api/v1/charac/${characNo}/overTasks`);
				Message.success("操作成功!");
			} catch (e: any) {
				Message.error(e?.message || "操作失败");
			}
		}
	});
};

onMounted(() => {
	window.addEventListener("resize", () => {
		windowHeight.value = window.innerHeight - 250;
	});
	search();
});
</script>

<template>
	<div class="roles-manager">
		<a-card>
			<a-form layout="inline" :model="searchForm">
				<a-form-item label="账号">
					<a-input placeholder="所属账号" allow-clear v-model="searchForm.account" />
				</a-form-item>
				<a-form-item label="昵称">
					<a-input placeholder="角色名称" allow-clear v-model="searchForm.name" />
				</a-form-item>
				<a-form-item label="等级">
					<a-input-number v-model="searchForm.minLev" :min="1" :max="86" placeholder="最低等级" style="width: 120px" />
				</a-form-item>
				<a-form-item label="~">
					<a-input-number v-model="searchForm.maxLev" :min="1" :max="86" placeholder="最高等级" style="width: 120px" />
				</a-form-item>
				<a-form-item label="职业">
					<a-select placeholder="选择职业" style="width: 150px" allow-clear v-model="searchForm.job">
						<a-option label="全部职业" value="" />
						<a-option v-for="(job, index) in jobs" :key="index" :label="job.name" :value="index" />
					</a-select>
				</a-form-item>
				<a-form-item>
					<a-button type="primary" @click="search(true)">查询</a-button>
				</a-form-item>
			</a-form>
		</a-card>

		<a-table
			scrollbar
			:scroll="{ y: windowHeight }"
			style="margin-top: 10px;"
			:data="pageResult.list"
			:loading="loading"
			:pagination="false"
		>
			<template #empty>
				<div style="text-align: center; padding: 20px;">
					暂无数据
				</div>
			</template>
			<template #columns>
				<a-table-column title="ID" data-index="characNo" />
				<a-table-column title="昵称" data-index="characName" />
				<a-table-column title="所属账号" data-index="accountname" />
				<a-table-column title="职业" data-index="job">
					<template #cell="{ record }">
						{{ getJobName(record.job) }}
					</template>
				</a-table-column>
				<a-table-column title="等级" data-index="lev" />
				<a-table-column title="转职类型" data-index="growType">
					<template #cell="{ record }">
						{{ getGrowTypeName(record.job, record.growType) }}
					</template>
				</a-table-column>
				<a-table-column title="操作" :width="140">
					<template #cell="{ record }">
						<a-dropdown>
							<a-button size="small" type="primary">
								操作
							</a-button>
							<template #content>
								<a-doption @click="openEdit(record)">编辑/详情</a-doption>
									<a-doption @click="openSendMail(record)">发送邮件</a-doption>
								<a-doption @click="overTasks(record.characNo)">完成任务</a-doption>
							</template>
						</a-dropdown>
					</template>
				</a-table-column>
			</template>
		</a-table>

		<div style="display: flex; justify-content: flex-end; margin-top: 12px;">
			<a-pagination
				:current="searchForm.pageNum"
				:page-size="searchForm.pageSize"
				:total="pageResult.totalSize"
				show-total
				show-jumper
				show-page-size
				:page-size-options="[10, 20, 50, 100]"
				@change="onPageChange"
				@page-size-change="onPageSizeChange"
			/>
		</div>

		<a-modal
			v-model:visible="editOption.open"
			title="编辑角色属性"
			:width="820"
			:mask-closable="false"
			@ok="submitEdit"
			@cancel="editOption.open = false"
		>
			<div style="margin-bottom: 16px; font-size: 12px; color: #999;">
				PS: 修改角色属性时，请确保当前角色已退出登录，否则修改无效。
			</div>
			<a-form :model="editOption.form" layout="vertical" size="small" class="edit-form">
				<a-row :gutter="16">
					<a-col :span="12">
						<a-form-item label="昵称">
							<a-input v-model="editOption.form.characName" disabled />
						</a-form-item>
					</a-col>
					<a-col :span="12">
						<a-form-item label="等级">
							<a-input v-model="editOption.form.lev" disabled />
						</a-form-item>
					</a-col>
					<a-col :span="12">
						<a-form-item label="职业">
							<a-input v-model="editOption.form.job" disabled />
						</a-form-item>
					</a-col>
					<a-col :span="12">
						<a-form-item label="转职">
							<a-input v-model="editOption.form.growType" disabled />
						</a-form-item>
					</a-col>

					<a-col :span="12">
						<a-form-item label="HP">
							<a-slider v-model="editOption.form.maxHp" :min="1" :max="65535" />
						</a-form-item>
					</a-col>
					<a-col :span="12">
						<a-form-item label="MP">
							<a-slider v-model="editOption.form.maxMp" :min="1" :max="65535" />
						</a-form-item>
					</a-col>
					<a-col :span="12">
						<a-form-item label="物攻">
							<a-slider v-model="editOption.form.phyAttack" :min="1" :max="65535" />
						</a-form-item>
					</a-col>
					<a-col :span="12">
						<a-form-item label="物防">
							<a-slider v-model="editOption.form.phyDefense" :min="1" :max="65535" />
						</a-form-item>
					</a-col>
					<a-col :span="12">
						<a-form-item label="魔攻">
							<a-slider v-model="editOption.form.magAttack" :min="1" :max="65535" />
						</a-form-item>
					</a-col>
					<a-col :span="12">
						<a-form-item label="魔防">
							<a-slider v-model="editOption.form.magDefense" :min="1" :max="65535" />
						</a-form-item>
					</a-col>
					<a-col :span="12">
						<a-form-item label="跳跃">
							<a-slider v-model="editOption.form.jump" :min="1" :max="65535" />
						</a-form-item>
					</a-col>
					<a-col :span="12">
						<a-form-item label="硬直">
							<a-slider v-model="editOption.form.hitRecovery" :min="1" :max="65535" />
						</a-form-item>
					</a-col>
					<a-col :span="12">
						<a-form-item label="移速">
							<a-slider v-model="editOption.form.moveSpeed" :min="1" :max="65535" />
						</a-form-item>
					</a-col>
					<a-col :span="12">
						<a-form-item label="攻速">
							<a-slider v-model="editOption.form.attackSpeed" :min="1" :max="65535" />
						</a-form-item>
					</a-col>
					<a-col :span="12">
						<a-form-item label="施速">
							<a-slider v-model="editOption.form.castSpeed" :min="1" :max="65535" />
						</a-form-item>
					</a-col>
				</a-row>
			</a-form>
		</a-modal>

		<a-modal
			v-model:visible="sendMailOption.open"
			:title="`发送邮件到: ${sendMailOption.receiveCharacName}`"
			:width="720"
			:mask-closable="false"
			@ok="submitSendMail"
			@cancel="sendMailOption.open = false"
		>
			<a-form :model="mailForm" layout="vertical" size="small" class="mail-form">
				<a-row :gutter="16">
					<a-col :span="24">
            <div class="mail-item-row">
              <a-form-item label="发件人" style="flex: 1">
                <a-input v-model="mailForm.sendCharacName" disabled />
              </a-form-item>
              <a-form-item label="金币数量" style="width: 160px">
                <a-input-number v-model="mailForm.gold" :min="0" :max="10000000000" style="width: 100%" />
              </a-form-item>
            </div>
					</a-col>
					<a-col :span="24">
            <div class="mail-item-row">
              <a-form-item label="物品名称" style="flex: 1">
                <ItemPicker v-model="mailForm.item" style="width: 100%;"/>
              </a-form-item>
              <a-form-item label="物品数量" style="width: 160px">
                <a-input-number v-model="mailForm.addInfo" :min="1" :max="100000" />
              </a-form-item>
            </div>
					</a-col>
					<a-col :span="6">
						<a-form-item label="强化等级">
							<a-input-number v-model="mailForm.upgrade" :min="0" :max="31" style="width: 100%" />
						</a-form-item>
					</a-col>
					<a-col :span="6">
						<a-form-item label="锻造等级">
							<a-input-number v-model="mailForm.seperateUpgrade" :min="0" :max="31" style="width: 100%" />
						</a-form-item>
					</a-col>
          <a-col :span="6">
            <a-form-item label="增幅属性">
              <a-select v-model="mailForm.amplifyOption" placeholder="请选择属性" allow-clear style="flex: 1">
                <a-option label="体力" :value="1" />
                <a-option label="精神" :value="2" />
                <a-option label="力量" :value="3" />
                <a-option label="智力" :value="4" />
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="增幅值">
              <a-input-number v-model="mailForm.amplifyValue" :min="0" :max="65535" style="width: 160px" />
            </a-form-item>
          </a-col>
					<a-col :span="24">
						<a-form-item label="是否封装">
							<div class="mail-seal-row">
								<a-switch v-model="mailForm.sealFlag" />
								<span class="mail-tip">注意: 史诗装备 (即：所有金色名称的物品) 不可封装，否则引起游戏崩溃</span>
							</div>
						</a-form-item>
					</a-col>
				</a-row>
			</a-form>
		</a-modal>
	</div>
</template>

<style scoped lang="less">
.roles-manager {
	padding: 10px;
}

.edit-form :deep(.arco-form-item) {
	margin-bottom: 8px;
}

.edit-form :deep(.arco-form-item-label) {
	padding-bottom: 4px;
}

.mail-form :deep(.arco-form-item) {
	margin-bottom: 10px;
}

.mail-item-row,
.mail-amplify-row {
	display: flex;
	gap: 10px;
	align-items: center;
}

.mail-seal-row {
	display: flex;
	align-items: center;
	gap: 12px;
}

.mail-tip {
	color: #ef4444;
	font-size: 12px;
}
</style>
