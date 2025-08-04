"use client"

import React from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import * as z from "zod"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import { Textarea } from "@/components/ui/textarea"
import { Input } from "@/components/ui/input"
import { toast } from "sonner";
import { useAppSelector } from "@/common/hooks";

const formSchema = z.object({
  greeting: z.string().optional(),
  prompt: z.string().optional(),
  token: z.string().optional(),
  bailian_dashscope_api_key: z.string().optional(),
  agora_app_id: z.string().optional(),
})

interface SettingsDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSubmit?: (values: z.infer<typeof formSchema>) => void
  defaultValues?: {
    greeting?: string
    prompt?: string
    token?: string
    bailian_dashscope_api_key?: string
    agora_app_id?: string
  }
}

export default function SettingsDialog(props: SettingsDialogProps) {
  const { open, onOpenChange, onSubmit, defaultValues } = props
  const channel = useAppSelector((state) => state.global.options.channel);

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      greeting: defaultValues?.greeting || "",
      prompt: defaultValues?.prompt || "",
      token: defaultValues?.token || "",
      bailian_dashscope_api_key: defaultValues?.bailian_dashscope_api_key || "",
      agora_app_id: defaultValues?.agora_app_id || "",
    },
  })

  // 当对话框打开时，重置表单为当前值
  React.useEffect(() => {
    if (open) {
      form.reset({
        greeting: defaultValues?.greeting || "",
        prompt: defaultValues?.prompt || "",
        token: defaultValues?.token || "",
        bailian_dashscope_api_key: defaultValues?.bailian_dashscope_api_key || "",
        agora_app_id: defaultValues?.agora_app_id || "",
      });
    }
  }, [open, defaultValues, form]);

  function handleSubmit(values: z.infer<typeof formSchema>) {
    console.log("Form Values:", values)
    onSubmit?.(values)
    onOpenChange(false)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle>智能体设置</DialogTitle>
          <DialogDescription>
            在这里配置您的智能体设置。
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-6">
            <FormField
              control={form.control}
              name="greeting"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>问候语</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="请输入问候语，留空则使用默认问候语"
                      className="resize-none"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="prompt"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>提示词</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="请输入提示词，留空则使用默认提示词"
                      className="resize-none"
                      rows={4}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="agora_app_id"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>声网 App ID</FormLabel>
                  <FormControl>
                    <Input
                      placeholder="请输入您的声网 App ID"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="token"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>声网临时 Token</FormLabel>
                  <FormControl>
                    <div className="flex gap-2">
                      <Input
                        placeholder="请输入您的声网临时 Token"
                        {...field}
                      />
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => {
                          if (typeof navigator !== 'undefined' && navigator.clipboard) {
                            if (channel) {
                              navigator.clipboard.writeText(channel);
                              toast.success("频道名已复制到剪贴板");
                            } else {
                              toast.error("频道名未设置");
                            }
                          } else {
                            toast.error("剪贴板功能不可用");
                          }
                        }}
                      >
                        复制频道名
                      </Button>
                    </div>
                  </FormControl>
                  <div className="flex items-center gap-2 mt-2">
                    <Button
                      type="button"
                      variant="link"
                      size="sm"
                      className="p-0 h-auto text-blue-600 hover:text-blue-800"
                      onClick={() => {
                        window.open("https://console.shengwang.cn/overview", "_blank");
                      }}
                    >
                      前去获取声网配置 →
                    </Button>
                  </div>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="bailian_dashscope_api_key"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>百炼 API KEY</FormLabel>
                  <FormControl>
                    <Input
                      placeholder="请输入您的百炼 API KEY"
                      type="password"
                      {...field}
                    />
                  </FormControl>
                  <div className="flex items-center gap-2 mt-2">
                    <Button
                      type="button"
                      variant="link"
                      size="sm"
                      className="p-0 h-auto text-blue-600 hover:text-blue-800"
                      onClick={() => {
                        window.open("https://bailian.console.aliyun.com/?tab=model#/api-key", "_blank");
                      }}
                    >
                      前去获取百炼配置 →
                    </Button>
                  </div>
                  <FormMessage />
                </FormItem>
              )}
            />
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                取消
              </Button>
              <Button type="submit">
                保存设置
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}