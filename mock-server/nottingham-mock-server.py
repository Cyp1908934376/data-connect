#!/usr/bin/env python3
"""
宁波诺丁汉大学 API Mock Server
模拟接口:
  POST /unnc/rest/core/auth/login  — 登录获取 identitytoken
  GET  /unnc/ris/student-theses     — 获取学生论文列表（分页）

数据结构与真实 API (https://api.nottingham.edu.cn) 完全一致。
默认生成 1042 条论文数据，支持分页。

启动: python nottingham-mock-server.py [--port 8081] [--count 1042]
"""

import json
import uuid
import random
import argparse
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs

# ============================================================
# 数据模板 — 基于真实 API 返回结构
# ============================================================

THESIS_DEFS = [
    {
        "title_en": "What influences the dissemination of online rumour messages in social media: the role of message, communicator, channel and rumour features",
        "title_zh": "社交媒体中网络谣言传播的影响因素：消息、传播者、渠道与谣言特征的作用",
        "authorFirst": "Boying", "authorLast": "LI", "authorEmail": "boying.li@nottingham.edu.cn",
        "degreeUri": "/dk/atira/pure/studentthesis/studentthesistypes/studentthesis/phd",
        "degreeZh": "学术博士学位论文", "degreeEn": "PhD Thesis",
        "orgNameZh": "商学院", "orgNameEn": "Faculty of Business",
        "orgUuid": "7da44285-108d-4d4c-97ed-449767ab887a",
        "supervisorFirst": "Alain", "supervisorLast": "Chong",
        "supervisorUuid": "1d964b6b-272d-4dda-8b4e-7fc77d8745da",
        "pubYear": 2019, "pubMonth": 11, "pubDay": 16,
        "abstractEn": "Social media enables efficient and easy exchange of information. However, this not only enhances the sharing of valid information but also facilitates the dissemination of rumours which may have harmful impacts on individuals, companies and the society. To manage the impacts of rumours, it is important to understand the drivers and patterns of online rumour message dissemination. Considering the uniqueness of social media as information exchange platforms, this thesis aims to understand the themes and traits of online rumour topics and messages. This thesis seeks to develop a message-level framework to delineate how message features, communicator features and channel features influence the dissemination of online rumour messages and how rumour features moderate those effects.",
        "abstractZh": "社交媒体实现了信息的高效便捷交流，但这不仅增强了有效信息的共享，也促进了可能对个人、公司和社会产生有害影响的谣言传播。为了管理谣言的影响，了解网络谣言信息传播的驱动因素和模式至关重要。本文旨在理解网络谣言话题和消息的主题与特征，并建立消息层面的理论框架。",
        "keywords": ["Rumour", "rumour dissemination", "social media", "persuasive appeal", "emotion", "schemas"],
        "keywordsZh": ["谣言", "谣言传播", "社交媒体", "说服性诉求", "情绪", "图式"],
        "hasThesisPdf": True
    },
    {
        "title_en": "Experimental and numerical investigation in CO2 sequestration in deep saline aquifers",
        "title_zh": "深部咸水层CO2封存的实验与数值模拟研究",
        "authorFirst": "Wei", "authorLast": "ZHANG", "authorEmail": "wei.zhang@nottingham.edu.cn",
        "degreeUri": "/dk/atira/pure/studentthesis/studentthesistypes/studentthesis/phd",
        "degreeZh": "学术博士学位论文", "degreeEn": "PhD Thesis",
        "orgNameZh": "化学与环境工程系", "orgNameEn": "Department of Chemical and Environmental Engineering",
        "orgUuid": "8eb55331-1ddd-4e9f-b9c1-878f858f8f9b",
        "supervisorFirst": "Tao", "supervisorLast": "WU",
        "supervisorUuid": "2e075c7c-383e-4faa-9e1e-8fe88d9756eb",
        "pubYear": 2020, "pubMonth": 3, "pubDay": 21,
        "abstractEn": "Geological CO2 sequestration in deep saline aquifers has been identified as one of the most promising techniques to mitigate anthropogenic CO2 emissions. This thesis presents an integrated experimental and numerical investigation of CO2-brine-rock interactions during the sequestration process. Core flooding experiments were conducted under reservoir conditions to study the effects of CO2 injection on rock properties, including porosity and permeability changes.",
        "abstractZh": "深部咸水层CO2封存已被确定为缓解人为CO2排放最有前景的技术之一。本文对封存过程中的CO2-盐水-岩石相互作用进行了实验和数值综合研究。在储层条件下进行了岩心驱替实验，研究了CO2注入对岩石性质的影响。",
        "keywords": ["CO2 sequestration", "saline aquifer", "reactive transport", "mineral trapping", "core flooding"],
        "keywordsZh": ["CO2封存", "咸水层", "反应输运", "矿物捕获", "岩心驱替"],
        "hasThesisPdf": True
    },
    {
        "title_en": "Analysis, design and optimization of synchronous reluctance machines for electric vehicle applications",
        "title_zh": "电动汽车用同步磁阻电机的分析、设计与优化",
        "authorFirst": "Yuli", "authorLast": "CHEN", "authorEmail": "yuli.chen@nottingham.edu.cn",
        "degreeUri": "/dk/atira/pure/studentthesis/studentthesistypes/studentthesis/phd",
        "degreeZh": "学术博士学位论文", "degreeEn": "PhD Thesis",
        "orgNameZh": "电气与电子工程系", "orgNameEn": "Department of Electrical and Electronic Engineering",
        "orgUuid": "9fc66442-2eee-5fag-c0d2-989g969g9gac",
        "supervisorFirst": "Chris", "supervisorLast": "GERADA",
        "supervisorUuid": "3f186d8d-494g-5gbb-af2f-9gf99g0867fc",
        "pubYear": 2021, "pubMonth": 7, "pubDay": 15,
        "abstractEn": "Synchronous reluctance machines (SynRMs) have emerged as a promising alternative to permanent magnet synchronous machines for electric vehicle traction applications due to their robust rotor structure and absence of rare-earth magnets. This thesis presents a comprehensive study on the electromagnetic design, analysis, and multi-objective optimization of SynRMs for EV traction.",
        "abstractZh": "同步磁阻电机因其坚固的转子结构和无需稀土磁体而成为电动汽车牵引应用中永磁同步电机的有前景替代方案。本文对用于电动汽车牵引的同步磁阻电机的电磁设计、分析和多目标优化进行了全面研究。",
        "keywords": ["synchronous reluctance machine", "electric vehicle", "torque ripple", "optimization", "rotor design"],
        "keywordsZh": ["同步磁阻电机", "电动汽车", "转矩脉动", "优化", "转子设计"],
        "hasThesisPdf": True
    },
    {
        "title_en": "How internationalised school teachers construct cross-cultural identities in the Chinese context",
        "title_zh": "国际化学校教师在中国语境下如何建构跨文化身份",
        "authorFirst": "Adam", "authorLast": "POOLE", "authorEmail": "adam.poole@nottingham.edu.cn",
        "degreeUri": "/dk/atira/pure/studentthesis/studentthesistypes/studentthesis/edd",
        "degreeZh": "教育博士学位论文", "degreeEn": "Doctor of Education (EdD)",
        "orgNameZh": "国际传播系", "orgNameEn": "School of International Communications",
        "orgUuid": "a0g77553-3faa-6gch-d3i3-i93i080i0ibd",
        "supervisorFirst": "Xia", "supervisorLast": "LIN",
        "supervisorUuid": "4g297g9g-5a5h-6hcc-bg3g-ah0gh0i978gd",
        "pubYear": 2020, "pubMonth": 6, "pubDay": 8,
        "abstractEn": "This study explores how internationalised school teachers in China construct their cross-cultural identities through their professional practice. Using a qualitative case study approach, data were collected through semi-structured interviews, classroom observations, and document analysis from ten international school teachers across three major Chinese cities.",
        "abstractZh": "本研究探讨了中国国际化学校教师如何通过专业实践建构跨文化身份。采用质性案例研究方法，通过半结构化访谈、课堂观察和文献分析收集了来自中国三个主要城市十名教师的数据。",
        "keywords": ["cross-cultural identity", "internationalised schools", "teachers", "China", "identity construction"],
        "keywordsZh": ["跨文化身份", "国际化学校", "教师", "中国", "身份建构"],
        "hasThesisPdf": True
    },
    {
        "title_en": "Modelling of multiphase flow containing ionic liquids in a stirred tank reactor",
        "title_zh": "搅拌槽反应器中含离子液体多相流的建模研究",
        "authorFirst": "Xiaoming", "authorLast": "LI", "authorEmail": "xiaoming.li@nottingham.edu.cn",
        "degreeUri": "/dk/atira/pure/studentthesis/studentthesistypes/studentthesis/mres",
        "degreeZh": "研究型硕士学位论文", "degreeEn": "Master by Research",
        "orgNameZh": "机械、材料及制造工程系", "orgNameEn": "Dept of Mechanical, Materials and Manufacturing Engineering",
        "orgUuid": "b1h88664-4gbb-7hdi-e4j4-j04j191j0jce",
        "supervisorFirst": "Fang", "supervisorLast": "WANG",
        "supervisorUuid": "5h3a0hah-6b6i-7idd-ch4h-bi1hi1hj089he",
        "pubYear": 2021, "pubMonth": 12, "pubDay": 3,
        "abstractEn": "Ionic liquids have received considerable attention as green solvents for chemical processes due to their unique properties. This research develops a computational fluid dynamics (CFD) model to simulate the multiphase flow behaviour of ionic liquids in a stirred tank reactor, validated against experimental measurements using particle image velocimetry (PIV).",
        "abstractZh": "离子液体由于其独特性质作为化学过程绿色溶剂受到了广泛关注。本研究开发了计算流体动力学模型来模拟搅拌槽反应器中离子液体的多相流行为，并通过粒子图像测速仪实验测量进行了验证。",
        "keywords": ["ionic liquids", "multiphase flow", "CFD", "stirred tank", "PIV"],
        "keywordsZh": ["离子液体", "多相流", "计算流体动力学", "搅拌槽", "粒子图像测速"],
        "hasThesisPdf": True
    },
    {
        "title_en": "Online P2P lending industry: an international analysis of regulatory frameworks and platform performance",
        "title_zh": "在线P2P借贷行业：监管框架与平台绩效的国际比较分析",
        "authorFirst": "Jing", "authorLast": "ZHOU", "authorEmail": "jing.zhou@nottingham.edu.cn",
        "degreeUri": "/dk/atira/pure/studentthesis/studentthesistypes/studentthesis/master",
        "degreeZh": "硕士学位论文", "degreeEn": "Master Thesis",
        "orgNameZh": "商学院", "orgNameEn": "Faculty of Business",
        "orgUuid": "7da44285-108d-4d4c-97ed-449767ab887a",
        "supervisorFirst": "Min", "supervisorLast": "ZHAO",
        "supervisorUuid": "6i4b1ibi-7c7j-8jee-di5i-cj2ij2ik090if",
        "pubYear": 2022, "pubMonth": 1, "pubDay": 20,
        "abstractEn": "The online peer-to-peer lending industry has experienced rapid growth globally but faces diverse regulatory landscapes across jurisdictions. This thesis conducts an international comparative analysis of P2P lending regulations in China, the UK, and the US, examining how regulatory frameworks affect platform operational performance, default rates, and investor protection.",
        "abstractZh": "在线P2P借贷行业在全球经历了快速增长，但面临不同司法管辖区多样化的监管环境。本文对中国、英国和美国的P2P借贷监管进行了国际比较分析，考察了监管框架如何影响平台运营绩效、违约率和投资者保护。",
        "keywords": ["P2P lending", "regulatory framework", "platform performance", "international comparison"],
        "keywordsZh": ["P2P借贷", "监管框架", "平台绩效", "国际比较"],
        "hasThesisPdf": False
    },
    {
        "title_en": "WEEE recycling and developing novel, continuous particle separation techniques for improved metal recovery",
        "title_zh": "废旧电子电器设备回收与新型连续颗粒分离技术开发以提高金属回收率",
        "authorFirst": "Tao", "authorLast": "SUN", "authorEmail": "tao.sun@nottingham.edu.cn",
        "degreeUri": "/dk/atira/pure/studentthesis/studentthesistypes/studentthesis/phd",
        "degreeZh": "学术博士学位论文", "degreeEn": "PhD Thesis",
        "orgNameZh": "化学与环境工程系", "orgNameEn": "Department of Chemical and Environmental Engineering",
        "orgUuid": "8eb55331-1ddd-4e9f-b9c1-878f858f8f9b",
        "supervisorFirst": "Yang", "supervisorLast": "LIU",
        "supervisorUuid": "7j5c2jcj-8d8k-9kff-ej6j-dk3jk3kl101jg",
        "pubYear": 2021, "pubMonth": 9, "pubDay": 10,
        "abstractEn": "Waste electrical and electronic equipment (WEEE) is one of the fastest growing waste streams globally. This research develops novel continuous particle separation techniques for improving metal recovery from WEEE, combining physical separation methods with advanced sensor-based sorting technologies.",
        "abstractZh": "废旧电子电器设备是全球增长最快的废物流之一。本研究开发了新型连续颗粒分离技术，结合物理分离方法和先进的基于传感器的分选技术，以提高废旧电子电器设备中金属的回收率。",
        "keywords": ["WEEE recycling", "particle separation", "metal recovery", "sensor-based sorting"],
        "keywordsZh": ["废旧电子电器回收", "颗粒分离", "金属回收", "传感器分选"],
        "hasThesisPdf": True
    },
    {
        "title_en": "Dynamic modelling and simulation of turbulent bubbly flow in a bubble column reactor using CFD-PBM coupled approach",
        "title_zh": "基于CFD-PBM耦合方法的鼓泡塔反应器湍流气泡流动态建模与仿真",
        "authorFirst": "Weibin", "authorLast": "SHI", "authorEmail": "weibin.shi@nottingham.edu.cn",
        "degreeUri": "/dk/atira/pure/studentthesis/studentthesistypes/studentthesis/phd",
        "degreeZh": "学术博士学位论文", "degreeEn": "PhD Thesis",
        "orgNameZh": "机械、材料及制造工程系", "orgNameEn": "Dept of Mechanical, Materials and Manufacturing Engineering",
        "orgUuid": "b1h88664-4gbb-7hdi-e4j4-j04j191j0jce",
        "supervisorFirst": "Hao", "supervisorLast": "WU",
        "supervisorUuid": "8k6d3kdk-9e9l-algg-fk7k-el4kl4lm212kh",
        "pubYear": 2020, "pubMonth": 5, "pubDay": 18,
        "abstractEn": "Bubble column reactors are widely used in chemical and biochemical industries. This thesis develops a coupled CFD-PBM (population balance model) approach to simulate the dynamics of turbulent bubbly flow in a bubble column reactor, incorporating bubble coalescence and breakup mechanisms.",
        "abstractZh": "鼓泡塔反应器广泛用于化学和生物化学工业。本文开发了CFD-PBM耦合方法模拟鼓泡塔反应器中湍流气泡流的动力学，纳入气泡聚并和破碎机制以提高预测精度。",
        "keywords": ["bubble column", "CFD-PBM", "turbulent bubbly flow", "coalescence", "breakup"],
        "keywordsZh": ["鼓泡塔", "CFD-PBM", "湍流气泡流", "聚并", "破碎"],
        "hasThesisPdf": True
    },
    {
        "title_en": "A new machine learning based method for multi-GNSS data quality analysis and anomaly detection",
        "title_zh": "基于机器学习的新型多GNSS数据质量分析与异常检测方法",
        "authorFirst": "Yiming", "authorLast": "QUAN", "authorEmail": "yiming.quan@nottingham.edu.cn",
        "degreeUri": "/dk/atira/pure/studentthesis/studentthesistypes/studentthesis/phd",
        "degreeZh": "学术博士学位论文", "degreeEn": "PhD Thesis",
        "orgNameZh": "土木工程系", "orgNameEn": "Department of Civil Engineering",
        "orgUuid": "c2i99775-5hcc-8iei-f5k5-k15k202k1kdf",
        "supervisorFirst": "Li", "supervisorLast": "HUANG",
        "supervisorUuid": "9l7e4lel-bfam-bmhh-gl8l-fm5lm5mn323li",
        "pubYear": 2022, "pubMonth": 2, "pubDay": 28,
        "abstractEn": "With the deployment of multiple global navigation satellite systems (GNSS), quality analysis of GNSS observation data has become increasingly important. This thesis proposes a novel machine learning based method for automatic quality analysis and anomaly detection in multi-GNSS observations, using ensemble learning techniques.",
        "abstractZh": "随着多个全球导航卫星系统的部署，GNSS观测数据的质量分析变得越来越重要。本文提出了一种基于机器学习的新型方法，用于多GNSS观测数据的自动质量分析和异常检测，利用集成学习技术识别和分类信号异常。",
        "keywords": ["GNSS", "data quality", "anomaly detection", "machine learning", "ensemble learning"],
        "keywordsZh": ["全球导航卫星系统", "数据质量", "异常检测", "机器学习", "集成学习"],
        "hasThesisPdf": True
    },
    {
        "title_en": "Exploring teachers' and students' expectations of good English teachers in a Chinese university context",
        "title_zh": "中国大学语境下师生对优秀英语教师期望的探索研究",
        "authorFirst": "Ying", "authorLast": "HE", "authorEmail": "ying.he@nottingham.edu.cn",
        "degreeUri": "/dk/atira/pure/studentthesis/studentthesistypes/studentthesis/master",
        "degreeZh": "硕士学位论文", "degreeEn": "Master Thesis",
        "orgNameZh": "英语语言与文学系", "orgNameEn": "School of English Language and Literature",
        "orgUuid": "d3j00886-6idd-9jfj-g6l6-l26l313l2leg",
        "supervisorFirst": "Ma", "supervisorLast": "CHEN",
        "supervisorUuid": "0m8f5mfm-cgn-cnii-hm9m-gn6mn6no434mj",
        "pubYear": 2021, "pubMonth": 8, "pubDay": 25,
        "abstractEn": "Understanding expectations of good English teachers from both teachers' and students' perspectives is essential for improving English language teaching quality in Chinese higher education. This mixed-methods study investigates the perceived characteristics of effective English teachers through questionnaires and interviews with 300 students and 30 teachers.",
        "abstractZh": "从师生双重视角理解优秀英语教师的期望，对提高中国高等教育英语教学质量至关重要。本研究采用混合方法，通过对300名学生和30名教师的问卷调查和访谈，探究有效英语教师的感知特征。",
        "keywords": ["English teachers", "teacher expectations", "Chinese university", "mixed methods"],
        "keywordsZh": ["英语教师", "教师期望", "中国大学", "混合方法"],
        "hasThesisPdf": True
    },
]

UNNC_UUID = "89d65963-2048-41e7-b668-056c672c774d"
UNNC_NAME = [
    {"locale": "zh_CN", "value": "宁波诺丁汉大学"},
    {"locale": "en_GB", "value": "University of Nottingham Ningbo China"}
]
UNNC_ORG_TYPE = {
    "pureId": 1066,
    "uri": "/dk/atira/pure/organisation/organisationtypes/organisation/university",
    "term": {
        "formatted": False,
        "text": [
            {"locale": "zh_CN", "value": "大学"},
            {"locale": "en_GB", "value": "University"}
        ]
    }
}


def build_managing_org():
    return {
        "uuid": UNNC_UUID,
        "link": {"ref": "content", "href": f"https://research.nottingham.edu.cn/ws/api/524/organisational-units/{UNNC_UUID}"},
        "externalId": "10001",
        "externalIdSource": "synchronisedOrganisation",
        "name": {"formatted": False, "text": UNNC_NAME},
        "type": UNNC_ORG_TYPE
    }


def build_documents(base_url, doc_base, author_last, author_ext_id, title, has_pdf, eprint_id):
    docs = []
    # thesis PDF
    if has_pdf:
        safe_title = "".join(c for c in title[:40].upper() if c.isalnum())
        safe_filename = f"{author_last}_{author_ext_id}_{safe_title}.pdf"
        pdf_url = f"{base_url}/ws/files/{doc_base}/{safe_filename}"
        docs.append({
            "pureId": doc_base,
            "externalId": "doc1",
            "externalIdSource": "importedStudentThesis",
            "url": pdf_url,
            "creator": "Tony0x5a@outlook.com",
            "created": "2021-09-13T09:16:24.782+0800",
            "documentType": {
                "pureId": 2377,
                "uri": "/dk/atira/pure/core/document/studentthesisdoctypes/thesis",
                "term": {
                    "formatted": False,
                    "text": [
                        {"locale": "zh_CN", "value": "论文-已审核通过版本"},
                        {"locale": "en_GB", "value": "Thesis-as examined"}
                    ]
                }
            },
            "visibility": {
                "key": "BACKEND",
                "value": {
                    "formatted": False,
                    "text": [
                        {"locale": "zh_CN", "value": "后端 - 不公开"},
                        {"locale": "en_GB", "value": "Backend - Restricted"}
                    ]
                }
            }
        })
        doc_base += 1
    # change history
    docs.append({
        "pureId": doc_base,
        "externalId": f"change-history-eprint-{eprint_id}",
        "externalIdSource": "importedStudentThesis",
        "url": f"{base_url}/ws/files/{doc_base}/{eprint_id}-changehistory.html",
        "creator": "Tony0x5a@outlook.com",
        "created": "2021-09-13T09:16:24.782+0800",
        "documentType": {
            "pureId": 2431,
            "uri": "/dk/atira/pure/core/document/studentthesisdoctypes/change_history",
            "term": {
                "formatted": False,
                "text": [
                    {"locale": "zh_CN", "value": "变更历史"},
                    {"locale": "en_GB", "value": "Change history"}
                ]
            }
        },
        "visibility": {
            "key": "BACKEND",
            "value": {
                "formatted": False,
                "text": [
                    {"locale": "zh_CN", "value": "后端 - 不公开"},
                    {"locale": "en_GB", "value": "Backend - Restricted"}
                ]
            }
        }
    })
    return docs


def build_person_associations(pure_id, author_first, author_last, author_email, author_uuid, author_ext_id):
    return [{
        "pureId": pure_id,
        "externalId": author_email,
        "externalIdSource": "importedStudentThesis",
        "person": {
            "uuid": author_uuid,
            "link": {"ref": "content", "href": f"https://research.nottingham.edu.cn/ws/api/524/persons/{author_uuid}"},
            "externalId": author_ext_id,
            "externalIdSource": "synchronisedUnifiedPerson",
            "externallyManaged": True,
            "name": {"formatted": False, "text": [{"value": f"{author_first} {author_last}"}]}
        },
        "name": {"firstName": author_first, "lastName": author_last},
        "associationHidden": False,
        "correspondingAuthor": False,
        "personRole": {
            "pureId": 2335,
            "uri": "/dk/atira/pure/studentthesis/roles/studentthesis/author",
            "term": {
                "formatted": False,
                "text": [
                    {"locale": "zh_CN", "value": "作者"},
                    {"locale": "en_GB", "value": "Author"}
                ]
            }
        }
    }]


def build_supervisors(pure_id, sup_first, sup_last, sup_uuid, sup_ext_id, eprint_id):
    return [{
        "pureId": pure_id,
        "externalId": f"supervisor-eprint-{eprint_id}-1",
        "externalIdSource": "importedStudentThesis",
        "person": {
            "uuid": sup_uuid,
            "link": {"ref": "content", "href": f"https://research.nottingham.edu.cn/ws/api/524/persons/{sup_uuid}"},
            "externalId": sup_ext_id,
            "externalIdSource": "synchronisedUnifiedPerson",
            "externallyManaged": True,
            "name": {"formatted": False, "text": [{"value": f"{sup_first} {sup_last}"}]}
        },
        "name": {"firstName": sup_first, "lastName": sup_last},
        "associationHidden": False,
        "personRole": {
            "pureId": 2342,
            "uri": "/dk/atira/pure/studentthesis/roles/internalexternal/studentthesis/supervisor",
            "term": {
                "formatted": False,
                "text": [
                    {"locale": "zh_CN", "value": "导师"},
                    {"locale": "en_GB", "value": "Supervisor"}
                ]
            }
        }
    }]


def build_organisational_units(org_uuid, org_ext_id, org_name_zh, org_name_en):
    return [{
        "uuid": org_uuid,
        "link": {"ref": "content", "href": f"https://research.nottingham.edu.cn/ws/api/524/organisational-units/{org_uuid}"},
        "externalId": org_ext_id,
        "externalIdSource": "synchronisedOrganisation",
        "name": {
            "formatted": False,
            "text": [
                {"locale": "zh_CN", "value": org_name_zh},
                {"locale": "en_GB", "value": org_name_en}
            ]
        },
        "type": {
            "pureId": 1068,
            "uri": "/dk/atira/pure/organisation/organisationtypes/organisation/department",
            "term": {
                "formatted": False,
                "text": [
                    {"locale": "zh_CN", "value": "院系"},
                    {"locale": "en_GB", "value": "Department"}
                ]
            }
        }
    }]


def build_keyword_groups(kw_pure_id, keywords, keywords_zh):
    free_kw = []
    kid = kw_pure_id + 1
    for kw in keywords:
        free_kw.append({"pureId": kid, "locale": "en_GB", "freeKeywords": [kw]})
        kid += 1
    for kw in keywords_zh:
        free_kw.append({"pureId": kid, "locale": "zh_CN", "freeKeywords": [kw]})
        kid += 1
    return [{
        "pureId": kw_pure_id,
        "externalId": "keywordContainers",
        "externalIdSource": "importedStudentThesis",
        "logicalName": "keywordContainers",
        "type": {
            "term": {
                "formatted": False,
                "text": [
                    {"locale": "zh_CN", "value": "自由关键词"},
                    {"locale": "en_GB", "value": "Free Keywords"}
                ]
            }
        },
        "keywordContainers": [{
            "pureId": kw_pure_id + 100,
            "freeKeywords": free_kw
        }]
    }]


def build_awarding_institutions(pure_id, eprint_id):
    return [{
        "pureId": pure_id,
        "externalId": f"awarding-institution-{eprint_id}",
        "externalIdSource": "importedStudentThesis",
        "externalOrganisationalUnit": {
            "uuid": "42115359-4128-41c0-a7a6-b24a32da05e8",
            "link": {"ref": "content", "href": "https://research.nottingham.edu.cn/ws/api/524/external-organisations/42115359-4128-41c0-a7a6-b24a32da05e8"},
            "externalId": "315090",
            "externalIdSource": "scival",
            "externallyManaged": True,
            "name": {
                "formatted": False,
                "text": [
                    {"locale": "zh_CN", "value": "University of Nottingham"},
                    {"locale": "en_GB", "value": "University of Nottingham"}
                ]
            },
            "type": {
                "pureId": 12354,
                "uri": "/dk/atira/pure/ueoexternalorganisationtypes/ueoexternalorganisation/externalorganisation",
                "term": {
                    "formatted": False,
                    "text": [
                        {"locale": "zh_CN", "value": "外部机构"},
                        {"locale": "en_GB", "value": "External organisation"}
                    ]
                }
            }
        }
    }]


def generate_thesis(index, total_count, base_url="http://localhost:8081"):
    """生成一条论文记录，结构与真实 API 完全一致"""
    td = THESIS_DEFS[index % len(THESIS_DEFS)]
    eprint_id = 59000 + index
    pure_base = 102620000 + index * 100
    doc_base = pure_base + 2
    thesis_uuid = str(uuid.uuid4())
    title = td["title_en"]
    author_uuid = str(uuid.uuid4())
    author_ext_id = str(16515000 + index)

    safe_slug = "".join(c for c in title.lower() if c.isalnum() or c.isspace()).split()[:10]
    slug = "-".join(safe_slug)

    return {
        "pureId": pure_base,
        "externalId": f"eprint-{eprint_id}",
        "externalIdSource": "importedStudentThesis",
        "uuid": thesis_uuid,
        "title": title,
        "managingOrganisationalUnit": build_managing_org(),
        "awardDate": {"year": td["pubYear"], "month": td["pubMonth"], "day": td["pubDay"]},
        "confidential": False,
        "info": {
            "createdBy": "Tony0x5a@outlook.com",
            "createdDate": "2021-09-13T09:16:24.782+0800",
            "modifiedBy": "yilia.wang@nottingham.edu.cn",
            "modifiedDate": "2025-09-12T10:07:18.259+0800",
            "portalUrl": f"https://research.nottingham.edu.cn/zh/studentTheses/{thesis_uuid}",
            "prettyURLIdentifiers": [slug[:80]]
        },
        "type": {
            "pureId": pure_base - 31,
            "uri": td["degreeUri"],
            "term": {
                "formatted": False,
                "text": [
                    {"locale": "zh_CN", "value": td["degreeZh"]},
                    {"locale": "en_GB", "value": td["degreeEn"]}
                ]
            }
        },
        "language": {
            "pureId": 212,
            "uri": "/dk/atira/pure/core/languages/en_GB",
            "term": {
                "formatted": False,
                "text": [
                    {"locale": "zh_CN", "value": "英语"},
                    {"locale": "en_GB", "value": "English"}
                ]
            }
        },
        "abstract": {
            "formatted": True,
            "text": [
                {"locale": "en_GB", "value": td["abstractEn"]},
                {"locale": "zh_CN", "value": td["abstractZh"]}
            ]
        },
        "workflow": {
            "workflowStep": "approved",
            "value": {
                "formatted": False,
                "text": [
                    {"locale": "zh_CN", "value": "已验证"},
                    {"locale": "en_GB", "value": "Validated"}
                ]
            }
        },
        "visibility": {
            "key": "FREE",
            "value": {
                "formatted": False,
                "text": [
                    {"locale": "zh_CN", "value": "公开 - 无限制"},
                    {"locale": "en_GB", "value": "Public - No restriction"}
                ]
            }
        },
        "personAssociations": build_person_associations(
            pure_base + 10, td["authorFirst"], td["authorLast"],
            td["authorEmail"], author_uuid, author_ext_id
        ),
        "organisationalUnits": build_organisational_units(
            td["orgUuid"], f"old_100{10 + (index % 7) * 10:02d}",
            td["orgNameZh"], td["orgNameEn"]
        ),
        "supervisors": build_supervisors(
            pure_base + 13, td["supervisorFirst"], td["supervisorLast"],
            td["supervisorUuid"], str(1112000 + index), eprint_id
        ),
        "supervisorOrganisationalUnits": [{
            "uuid": UNNC_UUID,
            "link": {"ref": "content", "href": f"https://research.nottingham.edu.cn/ws/api/524/organisational-units/{UNNC_UUID}"},
            "externalId": "10001",
            "externalIdSource": "synchronisedOrganisation",
            "name": {"formatted": False, "text": UNNC_NAME},
            "type": UNNC_ORG_TYPE
        }],
        "awardingInstitutions": build_awarding_institutions(pure_base + 1, eprint_id),
        "keywordGroups": build_keyword_groups(pure_base + 5, td["keywords"], td["keywordsZh"]),
        "documents": build_documents(base_url, doc_base, td["authorLast"], author_ext_id, title, td["hasThesisPdf"], eprint_id)
    }


class NottinghamMockHandler(BaseHTTPRequestHandler):
    """HTTP 请求处理器"""

    # 类变量，在启动时设置
    thesis_data = []
    total_count = 0
    server_base_url = ""

    def log_message(self, format, *args):
        print(f"[{self.log_date_time_string()}] {args[0]}")

    def _send_json(self, data, status=200):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json;charset=UTF-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def _send_file(self, filename, content_type="application/pdf"):
        """生成一个按文件名区分的唯一 PDF"""
        title_bytes = filename.encode("utf-8")
        stream = f"BT /F1 12 Tf 50 800 Td ({filename}) Tj ET".encode("utf-8")
        stream_len = len(stream)

        # 手动拼 PDF，精确计算 xref 偏移
        header = b"%PDF-1.4\n"
        obj1 = b"1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
        obj2 = b"2 0 obj<</Type/Pages/Count 1/Kids[3 0 R]>>endobj\n"
        obj3 = b"3 0 obj<</Type/Page/MediaBox[0 0 595 842]/Parent 2 0 R/Resources<</Font<</F1 4 0 R>>>>/Contents 5 0 R>>endobj\n"
        obj4 = b"4 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj\n"
        obj5_header = b"5 0 obj<</Length " + str(stream_len).encode() + b">>stream\n"
        obj5_footer = b"\nendstream\nendobj\n"

        # 计算各对象偏移
        pos = [0] * 6
        cur = 0
        parts = [header, obj1, obj2, obj3, obj4, obj5_header]
        lengths = [len(p) for p in parts]

        pos[0] = cur; cur += lengths[0]
        pos[1] = cur; cur += lengths[1]
        pos[2] = cur; cur += lengths[2]
        pos[3] = cur; cur += lengths[3]
        pos[4] = cur; cur += lengths[4]
        pos[5] = cur; cur += lengths[5]

        # xref 表
        xref_offset = cur + len(stream) + len(obj5_footer)
        xref = (b"xref\n0 6\n"
            b"0000000000 65535 f \n"
            + f"{pos[1]:010d} 00000 n \n".encode()
            + f"{pos[2]:010d} 00000 n \n".encode()
            + f"{pos[3]:010d} 00000 n \n".encode()
            + f"{pos[4]:010d} 00000 n \n".encode()
            + f"{pos[5]:010d} 00000 n \n".encode())

        trailer = b"trailer<</Size 6/Root 1 0 R>>\nstartxref\n" + str(xref_offset).encode() + b"\n%%EOF"

        pdf = header + obj1 + obj2 + obj3 + obj4 + obj5_header + stream + obj5_footer + xref + trailer

        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(pdf)))
        self.send_header("Content-Disposition", f'attachment; filename="{filename}"')
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(pdf)

    def _check_auth(self):
        """检查 Cookie 中的 identitytoken"""
        cookie = self.headers.get("Cookie", "")
        return "identitytoken=" in cookie

    def do_POST(self):
        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/")

        if path == "/unnc/rest/core/auth/login":
            qs = parse_qs(parsed.query)
            user_name = qs.get("userName", [""])[0]
            password = qs.get("password", [""])[0]

            if user_name and password:
                token = str(uuid.uuid4()).replace("-", "") + str(uuid.uuid4()).replace("-", "")
                self._send_json({
                    "identitytoken": token,
                    "userId": "efile",
                    "state": True,
                    "userName": user_name
                })
            else:
                self._send_json({"state": False, "message": "Missing credentials"}, 400)
        else:
            self._send_json({"error": "Not found"}, 404)

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path.rstrip("/")

        # 文件下载：/ws/files/{fileId}/{filename}
        if path.startswith("/ws/files/"):
            filename = path.rsplit("/", 1)[-1] if "/" in path else "download.pdf"
            content_type = "text/html" if filename.endswith(".html") else "application/pdf"
            self._send_file(filename, content_type)
            return

        if path == "/unnc/ris/student-theses":
            if not self._check_auth():
                self._send_json({"error": "Unauthorized"}, 401)
                return

            qs = parse_qs(parsed.query)
            try:
                offset = int(qs.get("offset", ["0"])[0])
                size = int(qs.get("size", ["10"])[0])
            except (ValueError, TypeError):
                offset, size = 0, 10

            size = min(size, 100)  # 限制最大页大小
            total = len(self.thesis_data)
            page_items = self.thesis_data[offset:offset + size]

            self._send_json({
                "count": total,
                "pageInformation": {"offset": offset, "size": size},
                "items": page_items,
                "navigationLinks": [
                    {"ref": "self", "href": f"/unnc/ris/student-theses?offset={offset}&size={size}"}
                ]
            })
        else:
            self._send_json({"error": "Not found"}, 404)

    def do_OPTIONS(self):
        """CORS 预检"""
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Cookie")
        self.end_headers()


def create_handler_class(thesis_data):
    """创建一个绑定了数据的 handler 类"""
    class Handler(NottinghamMockHandler):
        pass
    Handler.thesis_data = thesis_data
    Handler.total_count = len(thesis_data)
    return Handler


def main():
    parser = argparse.ArgumentParser(description="宁波诺丁汉大学 API Mock Server")
    parser.add_argument("--port", type=int, default=8081, help="监听端口 (默认: 8081)")
    parser.add_argument("--count", type=int, default=1042, help="生成论文数据条数 (默认: 1042)")
    parser.add_argument("--host", type=str, default="0.0.0.0", help="监听地址 (默认: 0.0.0.0)")
    args = parser.parse_args()

    base_url = f"http://localhost:{args.port}"
    print(f"正在生成 {args.count} 条论文数据...")
    thesis_data = [generate_thesis(i, args.count, base_url) for i in range(args.count)]
    print(f"数据生成完成: {len(thesis_data)} 条")
    print(f"  - documents 统计: {sum(1 for t in thesis_data if len(t['documents']) >= 2)} 条有完整PDF")
    print(f"  - 示例 title: {thesis_data[0]['title'][:60]}...")

    HandlerClass = create_handler_class(thesis_data)
    HandlerClass.server_base_url = base_url
    server = HTTPServer((args.host, args.port), HandlerClass)

    print(f"\n{'='*60}")
    print(f"  Mock Server 已启动")
    print(f"  地址: http://localhost:{args.port}")
    print(f"  登录: POST /unnc/rest/core/auth/login?userName=test&password=test")
    print(f"  论文: GET  /unnc/ris/student-theses")
    print(f"  分页: GET  /unnc/ris/student-theses?offset=0&size=10")
    print(f"{'='*60}")
    print(f"\n在 data-connect 模板 400 中设置:")
    print(f"  params.mockMode = false")
    print(f"  params.apiUser  = test")
    print(f"  params.apiPassword = test")
    print(f"  并将 API URL 指向 http://localhost:{args.port}")
    print(f"\n按 Ctrl+C 停止服务\n")

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n服务已停止")
        server.shutdown()


if __name__ == "__main__":
    main()
