/* SPDX-License-Identifier: GPL-3.0-or-later
 * Bounded diagnostic adapter for EarPort's OwnsConnection sends.
 * Smart-routing packet layout: LibrePods 89356c08, AACPManager.kt.
 * No keys/settings requests. Loaded only into the temporary test daemon.
 */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <pthread.h>
#include <sys/socket.h>
#include <bluetooth/bluetooth.h>
#include <bluetooth/l2cap.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
static ssize_t (*real_send)(int,const void*,size_t,int);
static pthread_once_t once=PTHREAD_ONCE_INIT;
static void init(void){*(void **)(&real_send)=dlsym(RTLD_NEXT,"send");}
static void put(uint8_t *b,size_t *n,const void *s,size_t z){memcpy(b+*n,s,z);*n+=z;}
#define STR(s) put(b,&n,s,sizeof(s)-1)
#define BYTES(...) do{const uint8_t x[]={__VA_ARGS__};put(b,&n,x,sizeof x);}while(0)
static void extra(int fd,int flags){
 const char *peer=getenv("OPENPODS_HANDOFF_PEER_MAC"), *target=getenv("OPENPODS_HANDOFF_HEADSET_MAC");bdaddr_t remote,headset;struct sockaddr_l2 local,other;socklen_t len=sizeof local;
 if(!peer||!target||str2ba(peer,&remote)||str2ba(target,&headset))return;
 if(getsockname(fd,(struct sockaddr*)&local,&len)||local.l2_family!=AF_BLUETOOTH)return;
 len=sizeof other;if(getpeername(fd,(struct sockaddr*)&other,&len)||bacmp(&other.l2_bdaddr,&headset))return;
 char self[18];ba2str(&local.l2_bdaddr,self);uint8_t b[256]={4,0,4,0,0x10,0};size_t n=6;
 put(b,&n,&remote,6);BYTES(0x82,0,1,0xe5,0x4a);STR("PlayingApp");BYTES(0x56);STR("com.google.ios.youtube");BYTES(0x52);STR("HostStreamingState");BYTES(0x42);STR("NO");BYTES(0x49);STR("btAddress");BYTES(0x51);put(b,&n,self,17);STR("btName");BYTES(0x47);STR("Android");BYTES(0x58);STR("otherDevice");STR("AudioCategory");BYTES(0x31,0x2d,1);
 if(n>144){fprintf(stderr,"DIAG_MEDIA_PACKET_REJECTED\n");return;}
 ssize_t media=real_send(fd,b,144,flags);
 memset(b,0,sizeof b);b[0]=4;b[2]=4;b[4]=0x10;n=6;put(b,&n,&remote,6);BYTES(0x62,0,1,0xe5,0x4a);STR("localscore");BYTES(0x30,0x64,0x46);STR("reason");BYTES(0x48);STR("Hijackv2");BYTES(0x51);STR("audioRoutingScore");BYTES(0x31,0x2d,1,0x5f);STR("audioRoutingSetOwnershipToFalse");BYTES(1,0x4b);STR("remotescore");BYTES(0xa5);
 if(n!=112){fprintf(stderr,"DIAG_HIJACK_PACKET_REJECTED length=%zu\n",n);return;}
 ssize_t hijack=real_send(fd,b,n,flags);fprintf(stderr,"DIAG_SMART_ROUTING_SENT media=%zd hijack=%zd\n",media,hijack);
}
ssize_t send(int fd,const void *buf,size_t size,int flags){pthread_once(&once,init);if(!real_send)return -1;ssize_t r=real_send(fd,buf,size,flags);const uint8_t *b=buf;
 if(r==(ssize_t)size&&size==11&&b[0]==4&&b[1]==0&&b[2]==4&&b[3]==0&&b[4]==9&&b[5]==0&&b[6]==6&&b[7]==1)extra(fd,flags);return r;
}
