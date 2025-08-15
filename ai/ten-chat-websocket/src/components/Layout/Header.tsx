import { LogoIcon, SmallLogoIcon } from "@/components/Icon";
import { HeaderRoomInfo, HeaderActions } from "./HeaderComponents";
import { cn } from "@/lib/utils";

export default function Header(props: { className?: string }) {
  const { className } = props;
  return (
    <>
      {/* Header */}
      <header
        className={cn(
          "flex items-center justify-between bg-white shadow-sm border-b border-gray-200 p-2 md:p-4",
          className,
        )}
      >
        <div className="flex items-center space-x-2">
          {/* <LogoIcon className="hidden h-5 md:block" />
          <SmallLogoIcon className="block h-4 md:hidden" /> */}
          <h1 className="text-sm font-bold md:text-xl text-gray-800">
            实时对话
          </h1>
        </div>
        <HeaderRoomInfo />
        <HeaderActions />
      </header>
    </>
  );
}
