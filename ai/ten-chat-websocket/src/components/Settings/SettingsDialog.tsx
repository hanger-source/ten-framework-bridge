import React, { useEffect, useState } from "react"; // Import useState
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Textarea } from "@/components/ui/textarea";
import { Input } from "@/components/ui/input";
import { toast } from "sonner";
import { useAgentSettings } from "@/hooks/useAgentSettings"; // Import useAgentSettings
import { IAgentSettings } from "@/types"; // Import IAgentSettings
import { Switch } from "@/components/ui/switch";
import { ChevronDown, ChevronUp } from "lucide-react"; // Import icons
import { cn } from "@/lib/utils";

const agentSettingSchema = z.object({
  greeting: z.string().optional(),
  prompt: z.string().optional(),
  echoCancellation: z.boolean().optional(),
  noiseSuppression: z.boolean().optional(),
  autoGainControl: z.boolean().optional(),
});

type AgentSettingFormValues = z.infer<typeof agentSettingSchema>;

interface SettingsDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  defaultValues: AgentSettingFormValues & { env?: string };
  onSubmit: (values: AgentSettingFormValues) => void;
}

export default function SettingsDialog({
  open,
  onOpenChange,
  defaultValues,
  onSubmit,
}: SettingsDialogProps) {
  const { agentSettings, saveSettings } = useAgentSettings();
  const [showAudioSettings, setShowAudioSettings] = useState(false); // New state for collapsable section

  const form = useForm<AgentSettingFormValues>({
    resolver: zodResolver(agentSettingSchema),
    defaultValues: {
      greeting: agentSettings.greeting || "",
      prompt: agentSettings.prompt || "",
      echoCancellation: agentSettings.echoCancellation,
      noiseSuppression: agentSettings.noiseSuppression,
      autoGainControl: agentSettings.autoGainControl,
    },
  });

  useEffect(() => {
    if (open) {
      form.reset({
        greeting: defaultValues.greeting || "",
        prompt: defaultValues.prompt || "",
        echoCancellation: defaultValues.echoCancellation,
        noiseSuppression: defaultValues.noiseSuppression,
        autoGainControl: defaultValues.autoGainControl,
      });
    }
  }, [open, defaultValues, form]);

  const handleSubmit = (data: AgentSettingFormValues) => {
    const env = agentSettings.env; // Use existing env

    const newSettings: IAgentSettings = {
      greeting: data.greeting || "",
      prompt: data.prompt || "",
      env: env,
      echoCancellation: data.echoCancellation ?? true,
      noiseSuppression: data.noiseSuppression ?? true,
      autoGainControl: data.autoGainControl ?? true,
    };

    saveSettings(newSettings);
    onOpenChange(false);
    toast.success("设置已保存");
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[425px]">
        <DialogHeader>
          <DialogTitle>智能体设置</DialogTitle>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="greeting"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>问候语</FormLabel>
                  <FormControl>
                    <Input placeholder="" {...field} />
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
                  <FormLabel>提示语</FormLabel>
                  <FormControl>
                    <Textarea
                      placeholder="请输入提示词，留空则使用默认提示词"
                      className="resize-none"
                      rows={8}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="space-y-3 p-3 bg-gray-50 rounded-lg">
              <div
                className="flex items-center justify-between cursor-pointer"
                onClick={() => setShowAudioSettings(!showAudioSettings)}
              >
                <div className="text-xs font-medium text-gray-700 mb-2">
                  音频处理设置 (可能影响音质)
                </div>
                {showAudioSettings ? (
                  <ChevronUp className="h-4 w-4 text-gray-500" />
                ) : (
                  <ChevronDown className="h-4 w-4 text-gray-500" />
                )}
              </div>

              {showAudioSettings && (
                <div className={cn("space-y-3")}> {/* Added conditional rendering for audio settings content */}
                  <FormField
                    control={form.control}
                    name="autoGainControl"
                    render={({ field }) => (
                      <FormItem className="flex flex-row items-center justify-between rounded-lg border p-4 shadow-sm">
                        <div className="space-y-0.5">
                          <FormLabel>启用自动增益控制 (AGC)</FormLabel>
                        </div>
                        <FormControl>
                          <Switch
                            checked={field.value}
                            onCheckedChange={field.onChange}
                          />
                        </FormControl>
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name="noiseSuppression"
                    render={({ field }) => (
                      <FormItem className="flex flex-row items-center justify-between rounded-lg border p-4 shadow-sm">
                        <div className="space-y-0.5">
                          <FormLabel>启用噪声抑制 (NS)</FormLabel>
                        </div>
                        <FormControl>
                          <Switch
                            checked={field.value}
                            onCheckedChange={field.onChange}
                          />
                        </FormControl>
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name="echoCancellation"
                    render={({ field }) => (
                      <FormItem className="flex flex-row items-center justify-between rounded-lg border p-4 shadow-sm">
                        <div className="space-y-0.5">
                          <FormLabel>启用回声消除 (AEC)</FormLabel>
                        </div>
                        <FormControl>
                          <Switch
                            checked={field.value}
                            onCheckedChange={field.onChange}
                          />
                        </FormControl>
                      </FormItem>
                    )}
                  />
                </div>
              )}
            </div>

            <Button type="submit">保存</Button>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
