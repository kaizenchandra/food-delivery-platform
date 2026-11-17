"use client";
import {useEffect,useRef,useState} from 'react';
import NotificationChannels from './NotificationChannels';
type Api=(path:string,body?:unknown,key?:string)=>Promise<any>;
type Notice={id:string;type:string;resource_id:string;title:string;href:string;created_at:string;is_read:boolean};
export default function NotificationWorkspace({api,subject}:{api:Api;subject:string}){
 const [items,setItems]=useState<Notice[]>([]),[unread,setUnread]=useState(0),[cursor,setCursor]=useState<number|null>(null),[before,setBefore]=useState<number|null>(null),[message,setMessage]=useState('');const currentApi=useRef(api);currentApi.current=api;
 useEffect(()=>{let active=true;async function load(){try{const data=await currentApi.current('notifications'+(before?'?before='+before:''));if(active){setItems(data.items);setUnread(data.unreadCount);setCursor(data.nextCursor)}}catch(e){if(active)setMessage(String(e))}}load();const timer=setInterval(load,3000);return()=>{active=false;clearInterval(timer)}},[before]);
 async function read(n:Notice){try{await currentApi.current('notifications/'+n.id+'/read',{});setItems(old=>old.map(i=>i.id===n.id?{...i,is_read:true}:i));const data=await currentApi.current('notifications'+(before?'?before='+before:''));setUnread(data.unreadCount)}catch(e){setMessage(String(e))}}
 return <section className="aftercare" aria-label="Notification inbox"><h1>Your notifications</h1><p>Persistent in-app updates, with optional email, SMS and browser push delivery.</p><NotificationChannels api={api} subject={subject}/><p role="status">{unread} unread notifications</p>{message&&<p role="status">{message}</p>}<button onClick={()=>setBefore(null)}>Latest notifications</button>{items.length===0&&<p>No notifications yet.</p>}{items.map(n=><article key={n.id} data-notification={n.id}><h2>{n.title}</h2><p>{n.is_read?'Read':'Unread'} · {new Date(n.created_at).toLocaleString()}</p><a href={n.href}>View update</a>{!n.is_read&&<button onClick={()=>read(n)}>Mark as read</button>}</article>)}{cursor&&<button onClick={()=>setBefore(cursor)}>Older notifications</button>}</section>
}
